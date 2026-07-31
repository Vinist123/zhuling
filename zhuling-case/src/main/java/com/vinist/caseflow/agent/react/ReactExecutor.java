package com.vinist.caseflow.agent.react;

import com.vinist.domain.agent.adapter.port.IAgentExecutionEngine;
import com.vinist.domain.agent.adapter.port.IReactModelPort;
import com.vinist.domain.agent.adapter.port.IToolExecutorPort;
import com.vinist.domain.agent.model.react.ReactExecutionContext;
import com.vinist.domain.agent.model.react.ReactExecutionPolicy;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactExecutionResult;
import com.vinist.domain.agent.model.react.ReactExitReason;
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

    private void publishStepCompleted(ReactExecutionRequest request,
                                      int stepIndex,
                                      ReactModelResponse response,
                                      int toolCallCount) {
        TurnTelemetry telemetry = request.getTurnTelemetry();
        if (telemetry == null) {
            return;
        }
        // 兜底推送本轮 thought：若 ChatModelPort 未逐块发 reasoning.delta，则在此补发完整思维链
        if (response.getReasoning() != null && !response.getReasoning().isBlank()) {
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
            return Mono.just(result(ReactExitReason.MAX_STEPS_REACHED, null,
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
                        reactModelPort.call(request, modelContext)
                                .timeout(Duration.ofMillis(policy.getLlmTimeoutMs()))
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
        int toolCallCount = toolCalls.size();

        // 发布 step.completed（thought + 本轮工具意图已确定）
        publishStepCompleted(request, context.getCurrentStep(), response, toolCallCount);

        if (toolCallCount == 0) {
            return Mono.just(result(ReactExitReason.COMPLETED, response.getContent(),
                    context.getTotalToolCalls(), steps, null));
        }
        int nextToolCallCount = context.getTotalToolCalls() + toolCallCount;
        if (nextToolCallCount > request.getPolicy().getMaxToolCalls()) {
            return Mono.just(result(ReactExitReason.MAX_TOOL_CALLS_REACHED, null,
                    context.getTotalToolCalls(), steps, null));
        }

        // 实时发布工具调用起点（act 阶段），逐个工具在开始执行前推送
        toolCalls.forEach(toolCall -> publishToolStarted(request, toolCall));

        return executeTools(request, toolCalls)
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
            return ReactToolResult.builder()
                    .id(toolCall.getId())
                    .name(toolCall.getName())
                    .arguments(toolCall.getArguments())
                    .output(execResult.output())
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
