package com.vinist.caseflow.agent.react;

import com.vinist.domain.agent.adapter.port.IAgentExecutionEngine;
import com.vinist.domain.agent.adapter.port.IReactModelPort;
import com.vinist.domain.agent.adapter.port.IToolExecutorPort;
import com.vinist.domain.agent.model.react.ReactExecutionContext;
import com.vinist.domain.agent.model.react.ReactExecutionPolicy;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactExecutionResult;
import com.vinist.domain.agent.model.react.ReactExitReason;
import com.vinist.domain.agent.model.react.ReactModelChunk;
import com.vinist.domain.agent.model.react.ReactModelResponse;
import com.vinist.domain.agent.model.react.ReactStep;
import com.vinist.domain.agent.model.react.ReactToolCall;
import com.vinist.domain.agent.model.react.ReactToolResult;
import com.vinist.domain.agent.model.react.ToolExecutionResult;
import com.vinist.domain.agent.model.react.interceptor.ErrorNormalizeReactNodeInterceptor;
import com.vinist.domain.agent.model.react.interceptor.ObservationReactNodeInterceptor;
import com.vinist.domain.agent.model.react.interceptor.ReactNodeContext;
import com.vinist.domain.agent.model.react.interceptor.ReactNodeException;
import com.vinist.domain.agent.model.react.interceptor.ReactNodeInterceptorChain;
import com.vinist.domain.agent.model.react.interceptor.ReactNodeOutcome;
import com.vinist.domain.agent.model.react.interceptor.ReactNodeErrorCategory;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 单 Agent ReAct 执行器。
 *
 * <p>使用模型原生 tool_calls 驱动顺序循环。模型、工具和消息协议由基础设施适配器负责，
 * 执行器只维护步骤、预算和退出状态。</p>
 */
@Service
public class ReactExecutor implements IAgentExecutionEngine {

    private final IToolExecutorPort toolExecutorPort;
    private final IConversationStreamEventPublisher eventPublisher;
    private final ReactNodeInterceptorChain nodeInterceptorChain;

    /**
     * 跨 step 重复调用同一工具时附加到回灌文本的提示：告知模型该查询已执行过、以上为上次结果，
     * 引导其基于已有信息直接收敛作答，而非反复查询同一目标（防止模型陷入重复调用循环直至超时/耗尽预算）。
     */
    private static final String REPEATED_TOOL_HINT =
            "\n\n（系统提示：该工具已用相同参数执行过，以上为上次的结果。请勿重复调用同一工具，"
                    + "基于已有信息直接给出回答。）";

    public ReactExecutor(IToolExecutorPort toolExecutorPort,
                         IConversationStreamEventPublisher eventPublisher) {
        this.toolExecutorPort = toolExecutorPort;
        this.eventPublisher = eventPublisher;
        // 节点生命周期拦截器链：观测 + 错误归一化（基于自研 BusinessLinkedList 责任链装配）
        this.nodeInterceptorChain = new ReactNodeInterceptorChain(
                new ObservationReactNodeInterceptor(),
                new ErrorNormalizeReactNodeInterceptor());
    }

    @Override
    public Mono<ReactExecutionResult> execute(ReactExecutionRequest request, IReactModelPort reactModelPort) {
        validate(request, reactModelPort);

        ReactExecutionPolicy policy = request.getPolicy();
        Instant startedAt = Instant.now();
        if (policy.getMaxSteps() < 1) {
            return Mono.just(result(ReactExitReason.MAX_STEPS_REACHED, null, 0, List.of(), null))
                    .doOnNext(r -> stampTiming(r, startedAt));
        }

        ReactExecutionContext context = ReactExecutionContext.builder()
                .request(request)
                .startedAt(startedAt)
                .currentStep(1)
                .totalToolCalls(0)
                .build();

        return executeNext(request, reactModelPort, context, List.of())
                .onErrorResume(TimeoutException.class,
                        error -> Mono.just(result(ReactExitReason.LLM_TIMEOUT, null, 0, List.of(), error.getMessage())))
                .onErrorResume(ReactNodeException.class,
                        error -> Mono.just(mapNormalizedError(error)))
                .onErrorResume(error -> Mono.just(result(
                        ReactExitReason.MODEL_ERROR, null, 0, List.of(), error.getMessage())))
                .doOnNext(r -> stampTiming(r, startedAt))
                .doOnNext(r -> publishLoopCompleted(request, r));
    }

    private void stampTiming(ReactExecutionResult result, Instant startedAt) {
        result.setStartedAt(startedAt);
        result.setFinishedAt(Instant.now());
    }

    /** 将拦截器链归一化的节点异常映射为统一的循环退出结果 */
    private ReactExecutionResult mapNormalizedError(ReactNodeException error) {
        ReactNodeErrorCategory category = error.getCategory();
        ReactExitReason reason;
        switch (category) {
            case TIMEOUT -> reason = ReactExitReason.LLM_TIMEOUT;
            case TOOL_ERROR -> reason = ReactExitReason.MODEL_ERROR;
            case BUDGET_EXHAUSTED -> reason = ReactExitReason.MAX_TOOL_CALLS_REACHED;
            case POLICY_DENIED -> reason = ReactExitReason.MODEL_ERROR;
            default -> reason = ReactExitReason.MODEL_ERROR;
        }
        return result(reason, null, 0, List.of(), error.getMessage());
    }

    // ---- 过程事件发布（agent.step.started / agent.step.completed / agent.loop.completed）----
    // 事件仅在请求携带 TurnTelemetry 时发布（同步/流式路径均会传入，直接路径可缺省）。

    private void publishStepStarted(ReactExecutionRequest request, int stepIndex) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        // 步骤起点标记（前端用于渲染 "Step N" 节点），与实时 reasoning/tool 事件解耦
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepIndex", stepIndex);
        payload.put("phase", "thought");
        eventPublisher.publish(telemetry, ConversationStreamEventType.AGENT_STEP_STARTED, payload);
    }

    /**
     * 流式逐 chunk 透传推理过程：将模型累积式 reasoning 快照传给 publishReasoningDelta，
     * 由其内部维护快照、自动计算增量并发布 reasoning.delta 事件，实现逐 token 实时推送。
     *
     * <p>SpringAI 2.0.x 流式下每个 chunk 的 reasoning 为累积式快照（到目前的完整思维链），
     * 因此直接传入即可，无需自己算增量。</p>
     */
    private void publishReasoningChunk(ReactExecutionRequest request, ReactModelChunk chunk) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        if (chunk.getReasoning() == null || chunk.getReasoning().isBlank()) {
            return;
        }
        if (Boolean.FALSE.equals(telemetry.getReasoningContentEnabled())) {
            return;
        }
        eventPublisher.publishReasoningDelta(telemetry, chunk.getReasoning());
    }

    private void publishStepCompleted(ReactExecutionRequest request,
                                      int stepIndex,
                                      ReactModelResponse response,
                                      int toolCallCount) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        // 安全网：流式期间已由 publishReasoningChunk 逐 token 推送 reasoning.delta，
        // 此处若再发会被 reasoningSnapshot 去重为 no-op；仅在流式透传遗漏时兜底补发完整思维链
        if (response.getReasoning() != null && !response.getReasoning().isBlank()
                && !Boolean.FALSE.equals(telemetry.getReasoningContentEnabled())) {
            eventPublisher.publishReasoningDelta(telemetry, response.getReasoning());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepIndex", stepIndex);
        payload.put("thought", truncate(response.getReasoning(), 512));
        payload.put("content", truncate(response.getContent(), 512));
        payload.put("toolCallCount", toolCallCount);
        payload.put("toolNames", response.getToolCalls() != null
                ? response.getToolCalls().stream().map(ReactToolCall::getName).toList() : List.of());
        payload.put("hasReasoning", response.getReasoning() != null && !response.getReasoning().isBlank());
        eventPublisher.publish(telemetry, ConversationStreamEventType.AGENT_STEP_COMPLETED, payload);
    }

    private void publishToolStarted(ReactExecutionRequest request, ReactToolCall toolCall) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", toolCall.getId());
        payload.put("toolName", toolCall.getName());
        payload.put("inputPreview", truncate(toolCall.getArguments(), 512));
        eventPublisher.publish(telemetry, ConversationStreamEventType.TOOL_STARTED, payload);
    }

    private void publishToolCompleted(ReactExecutionRequest request,
                                      ReactToolCall toolCall,
                                      ReactToolResult result) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", toolCall.getId());
        payload.put("toolName", toolCall.getName());
        payload.put("toolType", result.getToolType());
        payload.put("serverName", result.getServerName());
        payload.put("durationMs", result.getDurationMs());
        payload.put("success", !result.isFailed());
        payload.put("outputPreview", truncate(result.getOutput(), 512));
        payload.put("errorMessage", result.isFailed() ? truncate(result.getOutput(), 256) : null);
        eventPublisher.publish(telemetry, ConversationStreamEventType.TOOL_COMPLETED, payload);
    }

    private void publishLoopCompleted(ReactExecutionRequest request, ReactExecutionResult result) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exitReason", result.getExitReason() != null ? result.getExitReason().name() : null);
        payload.put("stepCount", result.getSteps() != null ? result.getSteps().size() : 0);
        payload.put("totalToolCalls", result.getTotalToolCalls());
        payload.put("errorMessage", result.getErrorMessage());
        payload.put("startedAt", result.getStartedAt() != null ? result.getStartedAt().toString() : null);
        payload.put("finishedAt", result.getFinishedAt() != null ? result.getFinishedAt().toString() : null);
        eventPublisher.publish(telemetry, ConversationStreamEventType.AGENT_LOOP_COMPLETED, payload);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Mono<ReactExecutionResult> executeNext(ReactExecutionRequest request,
                                                    IReactModelPort reactModelPort,
                                                    ReactExecutionContext context,
                                                    List<ReactStep> completedSteps) {
        ReactExecutionPolicy policy = request.getPolicy();
        if (context.getCurrentStep() > policy.getMaxSteps()) {
            String fallback = buildBudgetFallback(request, extractLastStepContent(completedSteps), "执行步数");
            return Mono.just(result(ReactExitReason.MAX_STEPS_REACHED, fallback,
                    context.getTotalToolCalls(), completedSteps, null));
        }

        ReactExecutionContext modelContext = ReactExecutionContext.builder()
                .request(request)
                .startedAt(context.getStartedAt())
                .currentStep(context.getCurrentStep())
                .totalToolCalls(context.getTotalToolCalls())
                .completedSteps(completedSteps)
                .build();

        // 发布 step.started（即将调用模型：thought 阶段）
        publishStepStarted(request, context.getCurrentStep());

        ReactNodeContext modelNodeContext = ReactNodeContext.builder()
                .nodeType(ReactNodeContext.NodeType.MODEL)
                .stepIndex(context.getCurrentStep())
                .agentId(request.getAgentId())
                .request(request)
                .executionContext(modelContext)
                .build();

        return nodeInterceptorChain.around(modelNodeContext, ctx ->
                        reactModelPort.stream(request, modelContext)
                                .timeout(Duration.ofMillis(policy.getLlmTimeoutMs()))
                                .doOnNext(chunk -> publishReasoningChunk(request, chunk))
                                .collectList()
                                .map(ReactModelChunk::aggregate)
                                .map(response -> ReactNodeOutcome.builder()
                                        .nodeType(ReactNodeContext.NodeType.MODEL)
                                        .stepIndex(context.getCurrentStep())
                                        .success(true)
                                        .modelResponse(response)
                                        .build()))
                .flatMap(outcome -> continueAfterModelResponse(
                        request, reactModelPort, modelContext, completedSteps, outcome.getModelResponse()));
    }

    private Mono<ReactExecutionResult> continueAfterModelResponse(ReactExecutionRequest request,
                                                                    IReactModelPort reactModelPort,
                                                                    ReactExecutionContext context,
                                                                    List<ReactStep> completedSteps,
                                                                    ReactModelResponse response) {
        List<ReactToolCall> toolCalls = response.getToolCalls() != null ? response.getToolCalls() : List.of();
        ReactStep step = ReactStep.builder()
                .index(context.getCurrentStep())
                .modelResponse(response)
                .toolResults(List.of())
                .build();
        List<ReactStep> steps = appendStep(completedSteps, step);
        // 模型偶尔会在一次响应里返回完全相同的多个 tool_call，去重后只执行一次，
        // 避免重复消耗 max-tool-calls 预算（结果仍按原始 tool_call id 逐一回填）
        List<ReactToolCall> uniqueToolCalls = dedupeToolCalls(toolCalls);
        int toolCallCount = uniqueToolCalls.size();

        // 发布 step.completed（thought + 本轮工具意图已确定）
        publishStepCompleted(request, context.getCurrentStep(), response, toolCallCount);

        if (toolCallCount == 0) {
            return Mono.just(result(ReactExitReason.COMPLETED, response.getContent(),
                    context.getTotalToolCalls(), steps, null));
        }
        int nextToolCallCount = context.getTotalToolCalls() + toolCallCount;
        if (nextToolCallCount > request.getPolicy().getMaxToolCalls()) {
            String fallback = buildBudgetFallback(request, response.getContent(), "工具调用");
            return Mono.just(result(ReactExitReason.MAX_TOOL_CALLS_REACHED, fallback,
                    context.getTotalToolCalls(), steps, null));
        }

        // 跨 step 重复调用防护：已完成步骤里用相同 (工具名+入参) 执行过的，直接复用上次结果，
        // 不再重复执行；回灌文本附加"请勿重复调用"提示，引导模型尽快收敛、打破重复循环
        Map<String, ReactToolResult> executedCache = collectExecutedResults(completedSteps);
        List<ReactToolCall> toExecute = new java.util.ArrayList<>();
        List<ReactToolResult> reusedResults = new java.util.ArrayList<>();
        for (ReactToolCall toolCall : uniqueToolCalls) {
            ReactToolResult cached = executedCache.get(toolCallKey(toolCall.getName(), toolCall.getArguments()));
            if (cached != null) {
                // 复用：仍发布 started/completed 让前端看到这次调用意图，但不真正执行工具
                publishToolStarted(request, toolCall);
                ReactToolResult reused = ReactToolResult.builder()
                        .id(toolCall.getId())
                        .name(cached.getName())
                        .arguments(cached.getArguments())
                        .output(cached.getOutput() + REPEATED_TOOL_HINT)
                        .failed(cached.isFailed())
                        .durationMs(0L)
                        .serverName(cached.getServerName())
                        .toolType(cached.getToolType())
                        .build();
                publishToolCompleted(request, toolCall, reused);
                reusedResults.add(reused);
            } else {
                toExecute.add(toolCall);
            }
        }
        // 实时发布实际执行工具的调用起点（act 阶段），逐个工具在开始执行前推送
        toExecute.forEach(toolCall -> publishToolStarted(request, toolCall));

        Mono<List<ReactToolResult>> execMono = toExecute.isEmpty()
                ? Mono.just(List.of())
                : executeTools(request, toExecute);
        return execMono
                .map(execResults -> {
                    List<ReactToolResult> merged = new java.util.ArrayList<>(execResults);
                    merged.addAll(reusedResults);
                    return merged;
                })
                .map(uniqueResults -> expandToolResults(toolCalls, uniqueResults))
                .flatMap(toolResults -> {
                    step.setToolResults(toolResults);
                    ReactExecutionContext nextContext = ReactExecutionContext.builder()
                            .request(request)
                            .startedAt(context.getStartedAt())
                            .currentStep(context.getCurrentStep() + 1)
                            .totalToolCalls(nextToolCallCount)
                            .completedSteps(steps)
                            .build();
                    return executeNext(request, reactModelPort, nextContext, steps);
                });
    }

    /** 按「工具名 + 入参」去重，保留首次出现的调用（模型偶发重复 tool_call） */
    private List<ReactToolCall> dedupeToolCalls(List<ReactToolCall> toolCalls) {
        java.util.Map<String, ReactToolCall> unique = new java.util.LinkedHashMap<>();
        for (ReactToolCall toolCall : toolCalls) {
            unique.putIfAbsent(toolCallKey(toolCall.getName(), toolCall.getArguments()), toolCall);
        }
        return List.copyOf(unique.values());
    }

    /**
     * 汇总已完成步骤里已执行的工具结果，key = 工具名 + 入参。
     * 用于跨 step 检测重复调用：模型在后续步骤用相同参数再调同一工具时复用上次结果，
     * 避免无限重复查询（相同入参的本地查询结果稳定，复用安全）。
     */
    private Map<String, ReactToolResult> collectExecutedResults(List<ReactStep> completedSteps) {
        Map<String, ReactToolResult> cache = new LinkedHashMap<>();
        if (completedSteps == null) {
            return cache;
        }
        for (ReactStep step : completedSteps) {
            if (step.getToolResults() == null) {
                continue;
            }
            for (ReactToolResult result : step.getToolResults()) {
                cache.putIfAbsent(toolCallKey(result.getName(), result.getArguments()), result);
            }
        }
        return cache;
    }

    /**
     * 将去重后的执行结果按原始 tool_call 列表展开。
     * 模型协议要求每个 tool_call id 都必须有对应的 tool 消息，因此重复项复用同一结果、仅换 id。
     */
    private List<ReactToolResult> expandToolResults(List<ReactToolCall> original,
                                                    List<ReactToolResult> uniqueResults) {
        java.util.Map<String, ReactToolResult> byKey = new java.util.HashMap<>();
        for (ReactToolResult result : uniqueResults) {
            byKey.putIfAbsent(toolCallKey(result.getName(), result.getArguments()), result);
        }
        java.util.List<ReactToolResult> expanded = new java.util.ArrayList<>();
        for (ReactToolCall toolCall : original) {
            ReactToolResult result = byKey.get(toolCallKey(toolCall.getName(), toolCall.getArguments()));
            if (result == null) {
                continue;
            }
            expanded.add(ReactToolResult.builder()
                    .id(toolCall.getId())
                    .name(result.getName())
                    .arguments(result.getArguments())
                    .output(result.getOutput())
                    .failed(result.isFailed())
                    .durationMs(result.getDurationMs())
                    .serverName(result.getServerName())
                    .toolType(result.getToolType())
                    .build());
        }
        return expanded;
    }

    private String toolCallKey(String name, String arguments) {
        return (name == null ? "" : name) + "|" + (arguments == null ? "" : arguments);
    }

    private Mono<List<ReactToolResult>> executeTools(ReactExecutionRequest request, List<ReactToolCall> toolCalls) {
        return reactor.core.publisher.Flux.fromIterable(toolCalls)
                .concatMap(toolCall -> {
                    ReactNodeContext toolNodeContext = ReactNodeContext.builder()
                            .nodeType(ReactNodeContext.NodeType.TOOL)
                            .stepIndex(0)
                            .agentId(request.getAgentId())
                            .toolName(toolCall.getName())
                            .toolInput(toolCall.getArguments())
                            .build();
                    return nodeInterceptorChain.around(toolNodeContext, ctx ->
                                    Mono.fromCallable(() -> executeTool(request.getAgentId(), toolCall))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .map(result -> ReactNodeOutcome.builder()
                                                    .nodeType(ReactNodeContext.NodeType.TOOL)
                                                    .stepIndex(0)
                                                    .success(!result.isFailed())
                                                    .toolResults(List.of(result))
                                                    .build()))
                            .map(ReactNodeOutcome::getToolResults)
                            .map(results -> results.isEmpty() ? null : results.get(0))
                            // 工具结果返回后实时发布 completed（observe 阶段）
                            .doOnNext(result -> { if (result != null) publishToolCompleted(request, toolCall, result); });
                })
                .collectList()
                .map(results -> results.stream().filter(java.util.Objects::nonNull).toList());
    }

    private ReactToolResult executeTool(String agentId, ReactToolCall toolCall) {
        try {
            ToolExecutionResult execResult = toolExecutorPort.executeWithMetadata(
                    agentId, toolCall.getName(), toolCall.getArguments());
            // 工具失败时 output 为 null；回灌给模型前归一化为可读文本，
            // 既让模型知道失败原因，也避免下游 SDK 因 content 为 null 抛 NPE
            String output = execResult.output() != null ? execResult.output()
                    : "工具执行失败: " + (execResult.errorMessage() != null
                    ? execResult.errorMessage() : "未知错误");
            return ReactToolResult.builder()
                    .id(toolCall.getId())
                    .name(toolCall.getName())
                    .arguments(toolCall.getArguments())
                    .output(output)
                    .failed(!execResult.success())
                    .durationMs(execResult.durationMs())
                    .serverName(execResult.serverName())
                    .toolType(execResult.toolType())
                    .build();
        } catch (RuntimeException error) {
            return ReactToolResult.builder()
                    .id(toolCall.getId())
                    .name(toolCall.getName())
                    .arguments(toolCall.getArguments())
                    .output("工具执行失败: " + error.getMessage())
                    .failed(true)
                    .build();
        }
    }

    private List<ReactStep> appendStep(List<ReactStep> completedSteps, ReactStep step) {
        java.util.ArrayList<ReactStep> steps = new java.util.ArrayList<>(completedSteps);
        steps.add(step);
        return List.copyOf(steps);
    }

    private String extractLastStepContent(List<ReactStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        ReactStep last = steps.get(steps.size() - 1);
        if (last.getModelResponse() != null) {
            return last.getModelResponse().getContent();
        }
        return null;
    }

    private String buildBudgetFallback(ReactExecutionRequest request, String lastContent, String budgetType) {
        int limit = "工具调用".equals(budgetType)
                ? request.getPolicy().getMaxToolCalls()
                : request.getPolicy().getMaxSteps();
        if (lastContent != null && !lastContent.isBlank()) {
            return lastContent + "\n\n（提示：已达到" + budgetType + "上限 " + limit + "，以上为基于已有信息的回答）";
        }
        return "抱歉，我已达到" + budgetType + "上限（" + limit + "），暂时无法完成这个任务。"
                + "请尝试简化问题或换一种方式提问。";
    }

    private ReactExecutionResult result(ReactExitReason reason,
                                        String finalAnswer,
                                        int toolCallCount,
                                        List<ReactStep> steps,
                                        String errorMessage) {
        return ReactExecutionResult.builder()
                .finalAnswer(finalAnswer)
                .exitReason(reason)
                .totalToolCalls(toolCallCount)
                .steps(steps)
                .errorMessage(errorMessage)
                .build();
    }

    private void validate(ReactExecutionRequest request, IReactModelPort reactModelPort) {
        if (request == null) {
            throw new IllegalArgumentException("ReAct 执行请求不能为空");
        }
        if (reactModelPort == null) {
            throw new IllegalArgumentException("ReAct 模型端口不能为空");
        }
        if (request.getPolicy() == null) {
            request.setPolicy(ReactExecutionPolicy.builder().build());
        }
        if (request.getPolicy().getMaxToolCalls() < 0) {
            throw new IllegalArgumentException("最大工具调用次数不能小于 0");
        }
        if (request.getPolicy().getLlmTimeoutMs() < 1) {
            throw new IllegalArgumentException("LLM 超时必须大于 0");
        }
    }

}
