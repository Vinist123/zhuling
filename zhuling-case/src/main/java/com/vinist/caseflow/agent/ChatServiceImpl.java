package com.vinist.caseflow.agent;

import com.vinist.domain.agent.adapter.port.IAgentExecutionEngine;
import com.vinist.domain.agent.adapter.port.IReactModelPort;
import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.MessageMetadataPolicy;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.react.ReactExecutionPolicy;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactExecutionResult;
import com.vinist.domain.agent.model.react.ReactExitReason;
import com.vinist.domain.agent.model.react.ReactModelResponse;
import com.vinist.domain.agent.model.react.ReactStep;
import com.vinist.domain.agent.model.react.ReactToolResult;
import com.vinist.domain.agent.model.telemetry.ChatUsageMetrics;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEvent;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.ReasoningContentStatus;
import com.vinist.domain.agent.model.telemetry.ReactProcessTelemetry;
import com.vinist.domain.agent.model.telemetry.ReactStepSummary;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.model.telemetry.ToolCallTelemetry;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import com.vinist.domain.agent.service.IChatService;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import com.vinist.domain.agent.service.IMessageMetadataCollector;
import com.vinist.domain.agent.service.ISessionService;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务实现
 *
 * <p>编排单次对话的核心逻辑，支持多轮会话记忆与 Agent 系统提示词
 *
 * <p>同步模式：通过 ThreadLocal 传播 TurnTelemetry（同线程，工作正常）
 * <p>流式模式：通过参数传递 TurnTelemetry 引用（Reactor 线程不传播 ThreadLocal）
 */
@Slf4j
@Service
public class ChatServiceImpl implements IChatService {

    private final ISessionService sessionService;
    private final IMessageMetadataCollector messageMetadataCollector;
    private final ITurnTelemetryService turnTelemetryService;
    private final IAgentRuntimeRegistry agentRuntimeRegistry;
    private final IConversationStreamEventPublisher eventPublisher;
    private final IAgentExecutionEngine agentExecutionEngine;

    public ChatServiceImpl(ISessionService sessionService,
                           IMessageMetadataCollector messageMetadataCollector,
                           ITurnTelemetryService turnTelemetryService,
                           IAgentRuntimeRegistry agentRuntimeRegistry,
                           IConversationStreamEventPublisher eventPublisher,
                           IAgentExecutionEngine agentExecutionEngine) {
        this.sessionService = sessionService;
        this.messageMetadataCollector = messageMetadataCollector;
        this.turnTelemetryService = turnTelemetryService;
        this.agentRuntimeRegistry = agentRuntimeRegistry;
        this.eventPublisher = eventPublisher;
        this.agentExecutionEngine = agentExecutionEngine;
    }

    @Override
    public String chat(String sessionId, String message) {
        log.info("执行聊天: sessionId={}, message={}", sessionId, message);
        SessionEntity session = loadSession(sessionId);
        AgentRuntime runtime = getRuntime(session);
        long startedAtMs = System.currentTimeMillis();

        String systemPrompt = getSystemPrompt(runtime);
        // 加载历史消息（不含当前消息）
        List<MessageEntity> availableHistory = loadHistory(sessionId);
        List<MessageEntity> history = limitHistory(availableHistory, runtime);

        String telemetryPrompt = buildTelemetryPrompt(systemPrompt, history, message);
        TurnTelemetry turnTelemetry = turnTelemetryService.startTurn(
                session.getId(), session.getTargetId(), false, availableHistory.size(), message);
        turnTelemetryService.updateContext(turnTelemetry, availableHistory.size(), history.size() + 1, telemetryPrompt, resolveContextWindowTokens(runtime));
        applyObservabilityFlags(turnTelemetry, runtime);

        turnTelemetryService.bindCurrentTurn(turnTelemetry);
        try {
            persistMessage(buildUserMessage(session, message, false, turnTelemetry));
            String response = isReactEnabled(runtime)
                    ? executeReact(runtime, systemPrompt, history, message, turnTelemetry)
                    : runtime.getChatModelPort().call(systemPrompt, history, message);
            persistMessage(buildAssistantMessage(session, response, false, startedAtMs, null, turnTelemetry));
            return response;
        } catch (RuntimeException ex) {
            persistMessage(buildAssistantMessage(session, "", false, startedAtMs, ex, turnTelemetry));
            throw ex;
        } finally {
            turnTelemetryService.clearCurrentTurn();
        }
    }

    @Override
    public Flux<ConversationStreamEvent> streamChat(String sessionId, String message) {
        log.info("执行流式聊天: sessionId={}, message={}", sessionId, message);
        return sessionService.getSession(sessionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("会话不存在: " + sessionId)))
                .flatMapMany(session -> {
                    long startedAtMs = System.currentTimeMillis();
                    StringBuilder assistantContent = new StringBuilder();
                    AgentRuntime runtime = getRuntime(session);

                    return sessionService.getMessages(sessionId)
                            .collectList()
                            .flatMapMany(availableHistory -> {
                                String systemPrompt = getSystemPrompt(runtime);
                                List<MessageEntity> history = limitHistory(availableHistory, runtime);
                                String telemetryPrompt = buildTelemetryPrompt(systemPrompt, history, message);
                                TurnTelemetry turnTelemetry = turnTelemetryService.startTurn(
                                        session.getId(), session.getTargetId(), true, availableHistory.size(), message);
                                turnTelemetryService.updateContext(
                                        turnTelemetry, availableHistory.size(), history.size() + 1, telemetryPrompt, resolveContextWindowTokens(runtime));
                                applyObservabilityFlags(turnTelemetry, runtime);
                                eventPublisher.open(turnTelemetry);
                                eventPublisher.publish(turnTelemetry, ConversationStreamEventType.TURN_STARTED,
                                        buildTurnStartedPayload(session, runtime, turnTelemetry));
                                turnTelemetryService.bindCurrentTurn(turnTelemetry);
                                MessageEntity userMessage = buildUserMessage(session, message, true, turnTelemetry);

                                Flux<ConversationStreamEvent> execution = sessionService.saveMessage(userMessage)
                                        .thenMany(
                                                isReactEnabled(runtime)
                                                        ? emitReactStream(runtime, systemPrompt, history, message, turnTelemetry)
                                                        : runtime.getChatModelPort().stream(systemPrompt, history, message, turnTelemetry)
                                        )
                                        .materialize()
                                        .concatMap(signal -> handleStreamSignal(
                                                signal, session, assistantContent, startedAtMs, turnTelemetry))
                                        .onErrorResume(error -> persistAssistantAndEmitTerminal(
                                                session, assistantContent.toString(), startedAtMs, turnTelemetry, error));

                                return eventPublisher.events(turnTelemetry.getTurnId())
                                        .mergeWith(execution)
                                        .doFinally(signalType -> {
                                            eventPublisher.complete(turnTelemetry);
                                            turnTelemetryService.clearCurrentTurn();
                                        });
                            });
                });
    }

    private Flux<ConversationStreamEvent> handleStreamSignal(Signal<String> signal,
                                                              SessionEntity session,
                                                              StringBuilder assistantContent,
                                                              long startedAtMs,
                                                              TurnTelemetry telemetry) {
        if (signal.isOnNext()) {
            String chunk = signal.get();
            assistantContent.append(chunk);
            eventPublisher.publish(telemetry, ConversationStreamEventType.MESSAGE_DELTA, Map.of("text", chunk));
            return Flux.empty();
        }

        if (signal.isOnComplete()) {
            return persistAssistantAndEmitTerminal(
                    session, assistantContent.toString(), startedAtMs, telemetry, null);
        }

        return persistAssistantAndEmitTerminal(
                session, assistantContent.toString(), startedAtMs, telemetry, signal.getThrowable());
    }

    private Flux<ConversationStreamEvent> persistAssistantAndEmitTerminal(SessionEntity session,
                                                                            String content,
                                                                            long startedAtMs,
                                                                            TurnTelemetry telemetry,
                                                                            Throwable error) {
        MessageEntity assistantMessage = buildAssistantMessage(
                session, content, true, startedAtMs, error, telemetry);
        Map<String, Object> metadata = assistantMessage.getMetadata();

        return sessionService.saveMessage(assistantMessage)
                .flatMapMany(messageId -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("assistantMessageId", messageId);
                    payload.put("metadata", metadata);
                    if (error == null) {
                        eventPublisher.publish(telemetry, ConversationStreamEventType.TURN_COMPLETED, payload);
                    } else {
                        payload.put("error", buildErrorPayload(error));
                        eventPublisher.publish(telemetry, ConversationStreamEventType.TURN_FAILED, payload);
                    }
                    return Flux.<ConversationStreamEvent>empty();
                })
                .onErrorResume(saveError -> {
                    log.error("保存流式助手消息失败: sessionId={}", session.getId(), saveError);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("error", buildErrorPayload(saveError));
                    payload.put("partialMetadata", metadata);
                    eventPublisher.publish(telemetry, ConversationStreamEventType.TURN_FAILED, payload);
                    return Flux.<ConversationStreamEvent>empty();
                });
    }

    private Map<String, Object> buildTurnStartedPayload(SessionEntity session,
                                                         AgentRuntime runtime,
                                                         TurnTelemetry telemetry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("id", session.getTargetId());
        target.put("type", session.getTargetType() != null ? session.getTargetType().name() : null);
        payload.put("target", target);
        payload.put("agentId", session.getTargetId());
        payload.put("model", resolveModelName(runtime));
        payload.put("traceId", telemetry.getTraceId());
        payload.put("requestId", telemetry.getRequestId());
        payload.put("context", telemetry.getContext());
        return payload;
    }

    private String resolveModelName(AgentRuntime runtime) {
        ModuleConfig moduleConfig = runtime.getConfig() != null ? runtime.getConfig().getModule() : null;
        ModuleConfig.ChatModelConfig chatModel = moduleConfig != null ? moduleConfig.getChatModel() : null;
        return chatModel != null ? chatModel.getModel() : null;
    }

    private Integer resolveContextWindowTokens(AgentRuntime runtime) {
        ModuleConfig moduleConfig = runtime.getConfig() != null ? runtime.getConfig().getModule() : null;
        ModuleConfig.ContextConfig contextConfig = moduleConfig != null ? moduleConfig.getContext() : null;
        return contextConfig != null ? contextConfig.getContextWindowTokens() : null;
    }

    /**
     * 将 observability 配置中的 reasoningContentEnabled / toolCallEnabled 写入 telemetry，
     * 供下游 SSE 事件发布时判断是否允许推送
     */
    private void applyObservabilityFlags(TurnTelemetry telemetry, AgentRuntime runtime) {
        if (telemetry == null || runtime == null) return;
        ModuleConfig moduleConfig = runtime.getConfig() != null ? runtime.getConfig().getModule() : null;
        ModuleConfig.ObservabilityConfig obsConfig = moduleConfig != null ? moduleConfig.getObservability() : null;
        if (obsConfig != null) {
            telemetry.setReasoningContentEnabled(obsConfig.getReasoningContentEnabled());
            telemetry.setToolCallEnabled(obsConfig.getToolCallEnabled());
        }
    }

    private Map<String, Object> buildErrorPayload(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", error.getClass().getName());
        payload.put("message", truncate(error.getMessage(), MessageMetadataPolicy.MAX_ERROR_MESSAGE_LENGTH));
        return payload;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private SessionEntity loadSession(String sessionId) {
        return sessionService.getSession(sessionId)
                .blockOptional()
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
    }

    private void persistMessage(MessageEntity message) {
        sessionService.saveMessage(message).block();
    }

    private String getSystemPrompt(AgentRuntime runtime) {
        AgentConfigModel config = runtime.getConfig();
        if (config != null && config.getAgent() != null && config.getAgent().getAgentDesc() != null) {
            String desc = config.getAgent().getAgentDesc().trim();
            return desc.isEmpty() ? null : desc;
        }
        return null;
    }

    private String executeReact(AgentRuntime runtime,
                                String systemPrompt,
                                List<MessageEntity> history,
                                String message,
                                TurnTelemetry telemetry) {
        IReactModelPort reactModelPort = runtime.getReactModelPort();
        if (reactModelPort == null) {
            throw new IllegalStateException("Agent 未初始化 ReAct 运行时: " + runtime.getAgentId());
        }
        ReactExecutionPolicy policy = resolveReactPolicy(runtime);
        ReactExecutionRequest request = ReactExecutionRequest.builder()
                .agentId(runtime.getAgentId())
                .systemPrompt(systemPrompt)
                .history(history)
                .userMessage(message)
                .policy(policy)
                .turnTelemetry(telemetry)
                .build();
        ReactExecutionResult result = agentExecutionEngine.execute(request, reactModelPort)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("ReAct 执行未返回结果"));
        // 先记录过程级观测，再判断退出原因；保证错误路径（非 COMPLETED 抛异常）也能落库
        if (telemetry != null) {
            telemetry.setReactProcess(buildReactProcess(result, policy));
            populateTelemetryFromReact(telemetry, result);
        }
        if (result.getExitReason() != ReactExitReason.COMPLETED) {
            throw new IllegalStateException("ReAct 执行未完成: " + result.getExitReason()
                    + (result.getErrorMessage() != null ? ", " + result.getErrorMessage() : ""));
        }
        return result.getFinalAnswer();
    }

    /**
     * 流式路径的 ReAct 适配：响应式订阅完整 ReAct 循环，
     * ReAct 循环期间的 thought/act/observe 通过 SSE 事件（reasoning.delta / tool.started /
     * tool.completed / agent.step.*）实时推送；最终答案在循环结束后拆成小块回放为
     * Flux&lt;String&gt;，使现有流式管道（materialize → handleStreamSignal → onErrorResume）无缝复用。
     *
     * <p>过程级观测（reactProcess / reasoning / usage / toolCalls）在结果返回时写入 telemetry，
     * 供终态事件 turn.completed 的 metadata 携带 react 段落库。
     */
    private Flux<String> emitReactStream(AgentRuntime runtime,
                                          String systemPrompt,
                                          List<MessageEntity> history,
                                          String message,
                                          TurnTelemetry telemetry) {
        IReactModelPort reactModelPort = runtime.getReactModelPort();
        if (reactModelPort == null) {
            return Flux.error(new IllegalStateException("Agent 未初始化 ReAct 运行时: " + runtime.getAgentId()));
        }
        ReactExecutionPolicy policy = resolveReactPolicy(runtime);
        ReactExecutionRequest request = ReactExecutionRequest.builder()
                .agentId(runtime.getAgentId())
                .systemPrompt(systemPrompt)
                .history(history)
                .userMessage(message)
                .policy(policy)
                .turnTelemetry(telemetry)
                .build();

        return agentExecutionEngine.execute(request, reactModelPort)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(result -> {
                    if (telemetry != null) {
                        telemetry.setReactProcess(buildReactProcess(result, policy));
                        populateTelemetryFromReact(telemetry, result);
                    }
                })
                .flatMapMany(result -> {
                    if (result.getExitReason() != ReactExitReason.COMPLETED) {
                        return Flux.error(new IllegalStateException("ReAct 执行未完成: " + result.getExitReason()
                                + (result.getErrorMessage() != null ? ", " + result.getErrorMessage() : "")));
                    }
                    return Flux.fromStream(splitToChunks(result.getFinalAnswer()));
                });
    }

    private static final int REACT_STREAM_CHUNK_SIZE = 4;

    private java.util.stream.Stream<String> splitToChunks(String text) {
        if (text == null || text.isEmpty()) {
            return java.util.stream.Stream.of("");
        }
        java.util.List<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += REACT_STREAM_CHUNK_SIZE) {
            chunks.add(text.substring(i, Math.min(i + REACT_STREAM_CHUNK_SIZE, text.length())));
        }
        return chunks.stream();
    }

    private ReactProcessTelemetry buildReactProcess(ReactExecutionResult result, ReactExecutionPolicy policy) {
        java.util.List<ReactStepSummary> summaries = new java.util.ArrayList<>();
        if (result.getSteps() != null) {
            for (ReactStep step : result.getSteps()) {
                summaries.add(buildStepSummary(step));
            }
        }
        Long durationMs = result.getStartedAt() != null && result.getFinishedAt() != null
                ? Math.max(0L, Duration.between(result.getStartedAt(), result.getFinishedAt()).toMillis())
                : null;
        return ReactProcessTelemetry.builder()
                .exitReason(result.getExitReason() != null ? result.getExitReason().name() : null)
                .stepCount(result.getSteps() != null ? result.getSteps().size() : 0)
                .totalToolCalls(result.getTotalToolCalls())
                .maxSteps(policy.getMaxSteps())
                .maxToolCalls(policy.getMaxToolCalls())
                .llmTimeoutMs(policy.getLlmTimeoutMs())
                .startedAt(result.getStartedAt())
                .finishedAt(result.getFinishedAt())
                .durationMs(durationMs)
                .errorMessage(result.getErrorMessage())
                .steps(summaries)
                .build();
    }

    private ReactStepSummary buildStepSummary(ReactStep step) {
        ReactModelResponse response = step.getModelResponse();
        List<ReactToolResult> toolResults = step.getToolResults() != null ? step.getToolResults() : List.of();
        List<String> toolNames = toolResults.stream()
                .map(ReactToolResult::getName)
                .toList();
        int failedToolCount = (int) toolResults.stream()
                .filter(ReactToolResult::isFailed)
                .count();
        return ReactStepSummary.builder()
                .index(step.getIndex())
                .toolCallCount(toolResults.size())
                .toolNames(toolNames)
                .failedToolCount(failedToolCount)
                .hadReasoning(response != null && response.getReasoning() != null
                        && !response.getReasoning().isBlank())
                .contentLength(response != null && response.getContent() != null
                        ? response.getContent().length() : 0)
                .build();
    }

    /**
     * 从 ReAct 执行结果中提取 reasoning/usage/toolCalls 写入 TurnTelemetry，
     * 补全 ReAct 路径下被绕过的 ChatModelPortImpl 采集逻辑。
     *
     * <p>Reasoning：取最后一步（最终答案）的思维链，与直接流式路径行为一致。
     * <p>Usage：累加所有步骤的 token 用量（ReAct 循环可能多次调用模型）。
     * <p>ToolCalls：从 ReactToolResult 构建（含 durationMs/serverName/toolType，
     * 由 ToolExecutorPortImpl.executeWithMetadata 采集，不依赖 ThreadLocal）。
     */
    private void populateTelemetryFromReact(TurnTelemetry telemetry, ReactExecutionResult result) {
        if (telemetry == null || result == null || result.getSteps() == null) {
            return;
        }

        // 1. Reasoning content — 取最后一步的 reasoning（最终答案的思维链）
        ReactStep lastStep = result.getSteps().isEmpty()
                ? null : result.getSteps().get(result.getSteps().size() - 1);
        ReactModelResponse lastResponse = lastStep != null ? lastStep.getModelResponse() : null;
        String reasoning = lastResponse != null ? lastResponse.getReasoning() : null;
        if (reasoning != null && !reasoning.isBlank()) {
            telemetry.setReasoningStatus(ReasoningContentStatus.STABLE);
            boolean truncated = reasoning.length() > MessageMetadataPolicy.MAX_REASONING_CONTENT_LENGTH;
            telemetry.setReasoningContent(truncated
                    ? reasoning.substring(0, MessageMetadataPolicy.MAX_REASONING_CONTENT_LENGTH) : reasoning);
            telemetry.setReasoningContentTruncated(truncated);
            telemetry.setReasoningNote("已从 ReAct 最终步骤捕获 reasoning_content");
        } else {
            telemetry.setReasoningStatus(ReasoningContentStatus.UNSTABLE);
            telemetry.setReasoningNote("ReAct 路径：最终步骤未返回 reasoning_content");
        }

        // 2. Usage metrics — 累加所有步骤的 token 用量
        long promptTokens = 0L;
        long completionTokens = 0L;
        long totalTokens = 0L;
        boolean hasUsage = false;
        for (ReactStep step : result.getSteps()) {
            ReactModelResponse response = step.getModelResponse();
            if (response != null && response.getUsage() != null) {
                ChatUsageMetrics u = response.getUsage();
                if (u.getPromptTokens() != null) { promptTokens += u.getPromptTokens(); hasUsage = true; }
                if (u.getCompletionTokens() != null) { completionTokens += u.getCompletionTokens(); hasUsage = true; }
                if (u.getTotalTokens() != null) { totalTokens += u.getTotalTokens(); hasUsage = true; }
            }
        }
        if (hasUsage) {
            telemetry.setUsage(ChatUsageMetrics.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .build());
        }

        // 3. Tool calls — 从所有步骤的 ReactToolResult 构建遥测条目
        //    先清空，避免 TelemetryToolCallback 在同线程时可能产生的重复
        telemetry.getToolCalls().clear();
        for (ReactStep step : result.getSteps()) {
            if (step.getToolResults() == null) continue;
            for (ReactToolResult toolResult : step.getToolResults()) {
                telemetry.getToolCalls().add(ToolCallTelemetry.builder()
                        .toolName(toolResult.getName())
                        .toolType(toolResult.getToolType() != null ? toolResult.getToolType() : "function")
                        .serverName(toolResult.getServerName())
                        .inputPreview(truncate(toolResult.getArguments(), MessageMetadataPolicy.MAX_TOOL_PREVIEW_LENGTH))
                        .outputPreview(truncate(toolResult.getOutput(), MessageMetadataPolicy.MAX_TOOL_PREVIEW_LENGTH))
                        .durationMs(toolResult.getDurationMs())
                        .success(!toolResult.isFailed())
                        .errorType(toolResult.isFailed() ? "RuntimeException" : null)
                        .errorMessage(toolResult.isFailed()
                                ? truncate(toolResult.getOutput(), MessageMetadataPolicy.MAX_ERROR_MESSAGE_LENGTH)
                                : null)
                        .build());
            }
        }
    }

    private ReactExecutionPolicy resolveReactPolicy(AgentRuntime runtime) {
        ModuleConfig moduleConfig = runtime.getConfig() != null ? runtime.getConfig().getModule() : null;
        ModuleConfig.ReactConfig reactConfig = moduleConfig != null ? moduleConfig.getReact() : null;
        return ReactExecutionPolicy.builder()
                .maxSteps(reactConfig != null && reactConfig.getMaxSteps() != null
                        ? reactConfig.getMaxSteps() : ReactExecutionPolicy.DEFAULT_MAX_STEPS)
                .maxToolCalls(reactConfig != null && reactConfig.getMaxToolCalls() != null
                        ? reactConfig.getMaxToolCalls() : ReactExecutionPolicy.DEFAULT_MAX_TOOL_CALLS)
                .llmTimeoutMs(reactConfig != null && reactConfig.getLlmTimeoutMs() != null
                        ? reactConfig.getLlmTimeoutMs() : ReactExecutionPolicy.DEFAULT_LLM_TIMEOUT_MS)
                .build();
    }

    private boolean isReactEnabled(AgentRuntime runtime) {
        ModuleConfig moduleConfig = runtime.getConfig() != null ? runtime.getConfig().getModule() : null;
        ModuleConfig.ObservabilityConfig observabilityConfig = moduleConfig != null
                ? moduleConfig.getObservability() : null;
        return observabilityConfig != null && Boolean.TRUE.equals(observabilityConfig.getReactEnabled());
    }

    /**
     * 加载会话的历史消息
     * <p>用于构建多轮对话上下文传递给 LLM
     */
    private List<MessageEntity> loadHistory(String sessionId) {
        return sessionService.getMessages(sessionId)
                .collectList()
                .block();
    }

    private AgentRuntime getRuntime(SessionEntity session) {
        if (session.getTargetType() != null && session.getTargetType() != ChatTargetType.AGENT) {
            throw new IllegalArgumentException("当前目标类型暂不支持直接聊天: " + session.getTargetType());
        }
        return agentRuntimeRegistry.getRequired(session.getTargetId());
    }

    private List<MessageEntity> limitHistory(List<MessageEntity> history, AgentRuntime runtime) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        ModuleConfig moduleConfig = runtime.getConfig().getModule();
        ModuleConfig.ContextConfig contextConfig = moduleConfig != null ? moduleConfig.getContext() : null;
        int maxMessages = contextConfig != null && contextConfig.getMaxMessages() != null
                && contextConfig.getMaxMessages() > 0 ? contextConfig.getMaxMessages() : history.size();
        int maxCharacters = contextConfig != null && contextConfig.getMaxCharacters() != null
                && contextConfig.getMaxCharacters() > 0 ? contextConfig.getMaxCharacters() : Integer.MAX_VALUE;

        int characters = 0;
        int selectedCount = 0;
        for (int index = history.size() - 1; index >= 0 && selectedCount < maxMessages; index--) {
            MessageEntity entity = history.get(index);
            int entityCharacters = entity.getContent() != null ? entity.getContent().length() : 0;
            if (selectedCount > 0 && characters + entityCharacters > maxCharacters) {
                break;
            }
            characters += entityCharacters;
            selectedCount++;
        }
        return history.subList(history.size() - selectedCount, history.size());
    }

    private String buildTelemetryPrompt(String systemPrompt, List<MessageEntity> history, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        appendPromptPart(prompt, systemPrompt);
        if (history != null) {
            for (MessageEntity entity : history) {
                appendPromptPart(prompt, entity.getRole());
                appendPromptPart(prompt, entity.getContent());
            }
        }
        appendPromptPart(prompt, userMessage);
        return prompt.toString();
    }

    private void appendPromptPart(StringBuilder prompt, String value) {
        if (value != null && !value.isBlank()) {
            prompt.append(value).append('\n');
        }
    }

    private MessageEntity buildUserMessage(SessionEntity session, String content, boolean stream, TurnTelemetry telemetry) {
        return MessageEntity.builder()
                .sessionId(session.getId())
                .role("user")
                .content(content)
//                .metadata(messageMetadataCollector.collectUserMessageMetadata(session, content, stream, telemetry))
                .metadata(null)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MessageEntity buildAssistantMessage(SessionEntity session,
                                                String content,
                                                boolean stream,
                                                long startedAtMs,
                                                Throwable error,
                                                TurnTelemetry telemetry) {
        return MessageEntity.builder()
                .sessionId(session.getId())
                .role("assistant")
                .content(content)
                .metadata(messageMetadataCollector.collectAssistantMessageMetadata(
                        session, content, stream, startedAtMs, error, telemetry))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private int safeMessageCount(SessionEntity session) {
        return session != null && session.getMessageCount() != null ? session.getMessageCount() : 0;
    }

}
