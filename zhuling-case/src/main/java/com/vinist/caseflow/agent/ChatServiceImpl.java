package com.vinist.caseflow.agent;

import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.MessageMetadataPolicy;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEvent;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
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

    public ChatServiceImpl(ISessionService sessionService,
                           IMessageMetadataCollector messageMetadataCollector,
                           ITurnTelemetryService turnTelemetryService,
                           IAgentRuntimeRegistry agentRuntimeRegistry,
                           IConversationStreamEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.messageMetadataCollector = messageMetadataCollector;
        this.turnTelemetryService = turnTelemetryService;
        this.agentRuntimeRegistry = agentRuntimeRegistry;
        this.eventPublisher = eventPublisher;
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
            String response = runtime.getChatModelPort().call(systemPrompt, history, message);
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
                                        .thenMany(runtime.getChatModelPort()
                                                .stream(systemPrompt, history, message, turnTelemetry)
                                                .materialize()
                                                .concatMap(signal -> handleStreamSignal(
                                                        signal, session, assistantContent, startedAtMs, turnTelemetry))
                                                .onErrorResume(error -> persistAssistantAndEmitTerminal(
                                                        session, assistantContent.toString(), startedAtMs, turnTelemetry, error)));

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
