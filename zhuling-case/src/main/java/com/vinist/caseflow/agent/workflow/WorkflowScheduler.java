package com.vinist.caseflow.agent.workflow;

import com.vinist.domain.agent.adapter.port.IAgentExecutionEngine;
import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.domain.agent.model.react.ReactExecutionPolicy;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactExecutionResult;
import com.vinist.domain.agent.model.react.ReactExitReason;
import com.vinist.domain.agent.model.react.ReactStep;
import com.vinist.domain.agent.model.react.ReactToolResult;
import com.vinist.domain.agent.model.workflow.WorkflowDefinition;
import com.vinist.domain.agent.model.workflow.WorkflowEdge;
import com.vinist.domain.agent.model.workflow.WorkflowExecutionResult;
import com.vinist.domain.agent.model.workflow.WorkflowExitReason;
import com.vinist.domain.agent.model.workflow.WorkflowNode;
import com.vinist.domain.agent.model.workflow.WorkflowNodeExecution;
import com.vinist.domain.agent.model.workflow.WorkflowNodeType;
import com.vinist.domain.agent.model.workflow.WorkflowRuntime;
import com.vinist.domain.agent.model.workflow.WorkflowState;
import com.vinist.domain.agent.model.workflow.interceptor.IWorkflowNodeInterceptor;
import com.vinist.domain.agent.model.workflow.interceptor.ObservationWorkflowNodeInterceptor;
import com.vinist.domain.agent.model.workflow.interceptor.WorkflowEventPublishingInterceptor;
import com.vinist.domain.agent.model.workflow.interceptor.WorkflowNodeChainTask;
import com.vinist.domain.agent.model.workflow.interceptor.WorkflowNodeContext;
import com.vinist.domain.agent.model.workflow.interceptor.WorkflowNodeInterceptorChain;
import com.vinist.domain.agent.model.workflow.interceptor.WorkflowNodeOutcome;
import com.vinist.domain.agent.model.telemetry.ChatUsageMetrics;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.ToolCallTelemetry;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.model.telemetry.WorkflowProcessTelemetry;
import com.vinist.domain.agent.model.MessageMetadataPolicy;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import com.vinist.domain.agent.service.IWorkflowScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Workflow 调度器（批次二 + 并行/Join 增强）。
 *
 * <p>从 entry 起按编译后的受控 DAG 执行：AGENT 节点委托 {@link IAgentExecutionEngine}
 * （每个节点为独立单轮执行，无会话历史，跨节点上下文经黑板传递）、CODE 节点执行
 * 显式注册组件、CONDITION 节点按组件路由跳转。</p>
 *
 * <p><strong>并行分支与 Join</strong>：非 CONDITION 节点允许配置多条<strong>无条件</strong>出边，
 * 调度期以 {@link Flux#merge} 并发执行各分支（多个 AGENT 节点同时跑）；一个节点若被 ≥2 条无条件边
 * 指向则作为<strong>汇聚（Join）节点</strong>，以屏障（{@link WorkflowState#arriveJoinBarrier}）
 * 等待所有并行分支到达后执行一次，其余分支在此终止。条件分支（CONDITION 的 when）互斥，不参与 Join。</p>
 *
 * <p>错误语义：节点失败不抛异常，以 {@code NODE_FAILED} 结果返回；并行分支中任一失败即整体
 * {@code NODE_FAILED}（短路，不等待 Join）；失败详情记录在对应 {@link WorkflowNodeExecution#getError()}。</p>
 */
@Slf4j
@Service
public class WorkflowScheduler implements IWorkflowScheduler {

    /** 防御性上限：DAG 无环由编译器保证，此为双保险 */
    private static final int MAX_NODE_EXECUTIONS = 100;

    private final IAgentExecutionEngine agentExecutionEngine;
    private final IConversationStreamEventPublisher eventPublisher;
    private final WorkflowNodeInterceptorChain nodeInterceptorChain;

    public WorkflowScheduler(IAgentExecutionEngine agentExecutionEngine,
                             IConversationStreamEventPublisher eventPublisher) {
        this.agentExecutionEngine = agentExecutionEngine;
        this.eventPublisher = eventPublisher;
        this.nodeInterceptorChain = new WorkflowNodeInterceptorChain(
                new ObservationWorkflowNodeInterceptor());
    }

    @Override
    public Mono<WorkflowExecutionResult> execute(WorkflowRuntime runtime, String userMessage,
                                                  TurnTelemetry telemetry) {
        if (runtime == null || runtime.getDefinition() == null) {
            return Mono.just(WorkflowExecutionResult.builder()
                    .exitReason(WorkflowExitReason.NODE_FAILED)
                    .errorMessage("Workflow 运行时为空")
                    .build());
        }
        WorkflowDefinition definition = runtime.getDefinition();
        Instant startedAt = Instant.now();
        WorkflowState state = new WorkflowState(definition.getInitialState());
        if (userMessage != null && !userMessage.isBlank()) {
            state.setVariable("userMessage", userMessage);
        }

        List<WorkflowNodeExecution> executions = Collections.synchronizedList(new ArrayList<>());
        // 并行分支并发写同一份执行记录，需线程安全 + 原子序号
        AtomicInteger indexCounter = new AtomicInteger(0);
        // 汇聚（Join）所需到达分支数：nodeId -> 无条件入边数（≥2 才需屏障）
        Map<String, Integer> joinRequired = computeJoinRequired(definition);
        // telemetry 非空时叠加事件发布拦截器（SSE workflow.node.*）
        WorkflowNodeInterceptorChain chain = telemetry == null ? nodeInterceptorChain
                : new WorkflowNodeInterceptorChain(new ObservationWorkflowNodeInterceptor(),
                        new WorkflowEventPublishingInterceptor(eventPublisher, telemetry));
        UsageAccumulator usageAccumulator = new UsageAccumulator();
        ExecCtx ctx = new ExecCtx(runtime, state, userMessage, executions, indexCounter,
                joinRequired, startedAt, chain, telemetry, usageAccumulator);
        return runFrom(ctx, definition.getEntryNodeId())
                .doOnNext(result -> {
                    populateTelemetry(telemetry, result, usageAccumulator);
                    publishWorkflowCompleted(telemetry, result);
                    log.info("Workflow 执行结束: id={}, exitReason={}, 节点数={}, 耗时={}ms",
                            definition.getId(), result.getExitReason(),
                            result.getExecutions() != null ? result.getExecutions().size() : 0,
                            result.getDurationMs());
                });
    }

    /** 单次执行上下文（不可变，随递归携带；executions 为共享线程安全列表） */
    private record ExecCtx(WorkflowRuntime runtime,
                           WorkflowState state,
                           String userMessage,
                           List<WorkflowNodeExecution> executions,
                           AtomicInteger indexCounter,
                           Map<String, Integer> joinRequired,
                           Instant startedAt,
                           WorkflowNodeInterceptorChain chain,
                           TurnTelemetry telemetry,
                           UsageAccumulator usageAccumulator) {
    }

    /**
     * 从指定节点开始递归执行。支持并行扇出（多无条件出边 → 并发分支）与 Join 屏障。
     */
    private Mono<WorkflowExecutionResult> runFrom(ExecCtx ctx, String nodeId) {
        WorkflowDefinition definition = ctx.runtime.getDefinition();
        if (ctx.indexCounter.get() >= MAX_NODE_EXECUTIONS) {
            return Mono.just(failed(ctx, "执行节点数超过上限: " + MAX_NODE_EXECUTIONS));
        }
        WorkflowNode node = definition.getNode(nodeId);
        if (node == null) {
            return Mono.just(failed(ctx, "节点不存在: " + nodeId));
        }

        // Join 屏障：汇聚节点需等待所有并行分支到达，仅最后到达者执行汇聚节点，其余分支在此终止
        Integer required = ctx.joinRequired.get(nodeId);
        if (required != null) {
            boolean isLast = ctx.state.arriveJoinBarrier(nodeId, required);
            if (!isLast) {
                return Mono.just(WorkflowExecutionResult.builder()
                        .workflowId(definition.getId())
                        .exitReason(WorkflowExitReason.COMPLETED)
                        .build());
            }
        }

        return executeNode(ctx, node)
                .flatMap(execution -> {
                    ctx.executions.add(execution);
                    if (!execution.isSuccess()) {
                        return Mono.just(failed(ctx, "节点 " + node.getNodeId()
                                + " 执行失败: " + execution.getError()));
                    }
                    List<String> next = selectedNextNodeIds(execution);
                    if (next.isEmpty()) {
                        return Mono.just(completed(ctx));
                    }
                    if (next.size() == 1) {
                        return runFrom(ctx, next.get(0));
                    }
                    // 并行扇出：并发执行各分支；任一失败则整体 NODE_FAILED（短路，不等待 Join）
                    List<Mono<WorkflowExecutionResult>> branches = next.stream()
                            .map(nextId -> runFrom(ctx, nextId))
                            .toList();
                    return Flux.merge(branches)
                            .collectList()
                            .map(results -> {
                                boolean anyFailed = results.stream()
                                        .anyMatch(r -> r.getExitReason() != WorkflowExitReason.COMPLETED);
                                return anyFailed ? failed(ctx, "并行分支中存在失败节点") : completed(ctx);
                            })
                            .onErrorResume(error -> Mono.just(failed(ctx, safeMessage(error))));
                })
                .onErrorResume(error -> Mono.just(failed(ctx, safeMessage(error))));
    }

    /** 执行单个节点：拦截器链包裹，输出结果（含失败归一化） */
    private Mono<WorkflowNodeExecution> executeNode(ExecCtx ctx, WorkflowNode node) {
        WorkflowNodeContext context = WorkflowNodeContext.builder()
                .workflowId(ctx.runtime.getWorkflowId())
                .nodeId(node.getNodeId())
                .nodeName(node.getName())
                .nodeType(node.getType())
                .index(ctx.indexCounter.incrementAndGet())
                .state(ctx.state)
                .build();
        Instant nodeStart = Instant.now();
        return ctx.chain.around(context, ctx2 -> runNode(ctx, node))
                .map(outcome -> WorkflowNodeExecution.builder()
                        .index(context.getIndex())
                        .nodeId(node.getNodeId())
                        .nodeName(node.getName())
                        .type(node.getType())
                        .output(outcome.getOutput())
                        .routedToNodeId(outcome.getRoutedToNodeId())
                        .routedToNodeIds(outcome.getRoutedToNodeIds())
                        .durationMs(durationMs(nodeStart))
                        .build())
                .onErrorResume(error -> Mono.just(WorkflowNodeExecution.builder()
                        .index(context.getIndex())
                        .nodeId(node.getNodeId())
                        .nodeName(node.getName())
                        .type(node.getType())
                        .durationMs(durationMs(nodeStart))
                        .error(safeMessage(error))
                        .build()));
    }

    /** 节点本体：按类型分发 */
    private Mono<WorkflowNodeOutcome> runNode(ExecCtx ctx, WorkflowNode node) {
        return Mono.defer(() -> switch (node.getType()) {
            case AGENT -> runAgentNode(ctx, node);
            case CODE -> runCodeNode(ctx, node);
            case CONDITION -> runConditionNode(ctx, node);
            case TOOL -> Mono.error(new IllegalStateException("TOOL 节点暂未支持"));
        });
    }

    /** AGENT 节点：渲染输入模板 → ReAct 引擎单轮执行 → 输出写黑板 */
    private Mono<WorkflowNodeOutcome> runAgentNode(ExecCtx ctx, WorkflowNode node) {
        WorkflowRuntime runtime = ctx.runtime;
        AgentRuntime agentRuntime = runtime.getAgentNodes().get(node.getNodeId());
        if (agentRuntime == null) {
            return Mono.error(new IllegalStateException("AGENT 节点未解析到 AgentRuntime: " + node.getNodeId()));
        }
        if (agentRuntime.getReactModelPort() == null) {
            return Mono.error(new IllegalStateException("AGENT 节点引用的 Agent 未启用 ReAct: "
                    + agentRuntime.getAgentId() + "（需开启 observability.react-enabled）"));
        }
        String input = hasText(node.getInputTemplate())
                ? ctx.state.renderTemplate(node.getInputTemplate())
                : ctx.userMessage;
        if (!hasText(input)) {
            return Mono.error(new IllegalStateException("AGENT 节点输入为空（无 input-template 且无用户消息）: "
                    + node.getNodeId()));
        }

        ReactExecutionRequest request = ReactExecutionRequest.builder()
                .agentId(agentRuntime.getAgentId())
                .systemPrompt(resolveSystemPrompt(agentRuntime))
                .history(List.of())
                .userMessage(input)
                .policy(resolveReactPolicy(agentRuntime))
                .turnTelemetry(ctx.telemetry)
                .build();
        return agentExecutionEngine.execute(request, agentRuntime.getReactModelPort())
                .doOnNext(result -> ctx.usageAccumulator.accumulate(result))
                .flatMap(result -> {
                    String answer = result.getFinalAnswer();
                    // 与 ChatServiceImpl 一致的优雅降级：非 COMPLETED 但有降级回答则接受
                    if (!hasText(answer)) {
                        return Mono.error(new IllegalStateException("AGENT 节点未产出回答: exitReason="
                                + result.getExitReason()
                                + (result.getErrorMessage() != null ? ", " + result.getErrorMessage() : "")));
                    }
                    return Mono.just(answer);
                })
                .map(answer -> {
                    writeOutput(ctx.state, node, answer);
                    return outcome(node, answer, parallelTargets(node));
                });
    }

    /** CODE 节点：执行显式注册组件 → 输出写黑板 */
    private Mono<WorkflowNodeOutcome> runCodeNode(ExecCtx ctx, WorkflowNode node) {
        var component = ctx.runtime.getCodeNodes().get(node.getNodeId());
        if (component == null) {
            return Mono.error(new IllegalStateException("CODE 节点未解析到组件: " + node.getNodeId()));
        }
        return component.execute(ctx.state)
                .map(output -> {
                    writeOutput(ctx.state, node, output);
                    return outcome(node, output, parallelTargets(node));
                })
                .switchIfEmpty(Mono.fromCallable(() -> outcome(node, null, parallelTargets(node))));
    }

    /** CONDITION 节点：路由组件选分支 → 匹配出边（单条，互斥路由） */
    private Mono<WorkflowNodeOutcome> runConditionNode(ExecCtx ctx, WorkflowNode node) {
        var component = ctx.runtime.getConditionNodes().get(node.getNodeId());
        if (component == null) {
            return Mono.error(new IllegalStateException("CONDITION 节点未解析到组件: " + node.getNodeId()));
        }
        return Mono.fromCallable(() -> component.route(ctx.state))
                .flatMap(branch -> {
                    if (branch == null || branch.isBlank()) {
                        return Mono.error(new IllegalStateException("CONDITION 组件返回空分支: "
                                + node.getNodeId()));
                    }
                    return Mono.just(branch);
                })
                .map(branch -> {
                    WorkflowEdge matched = node.getOutgoingEdges().stream()
                            .filter(edge -> branch.equals(edge.getWhen()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("CONDITION 节点路由未命中: node="
                                    + node.getNodeId() + ", branch=" + branch));
                    return outcome(node, branch, List.of(matched.getToNodeId()));
                });
    }

    private void writeOutput(WorkflowState state, WorkflowNode node, Object output) {
        if (node.getOutputKey() != null && !node.getOutputKey().isBlank() && output != null) {
            state.setNodeOutput(node.getOutputKey(), output);
        }
    }

    /** 终态遥测：workflowProcess 落 telemetry（metadata 落库数据源） + usage/工具调用汇总 */
    private void populateTelemetry(TurnTelemetry telemetry, WorkflowExecutionResult result,
                                   UsageAccumulator usageAccumulator) {
        if (telemetry == null) {
            return;
        }
        telemetry.setWorkflowProcess(WorkflowProcessTelemetry.from(
                result.getWorkflowId(),
                result.getExitReason() != null ? result.getExitReason().name() : null,
                result.getDurationMs(),
                result.getErrorMessage(),
                result.getExecutions()));
        ChatUsageMetrics usage = usageAccumulator.toMetrics();
        if (usage != null) {
            telemetry.setUsage(usage);
        }
        // 工具调用统一从各 AGENT 节点的执行结果重建（ThreadLocal 记录在工作流线程下不完整）
        telemetry.getToolCalls().clear();
        telemetry.getToolCalls().addAll(usageAccumulator.getToolCalls());
    }

    private void publishWorkflowCompleted(TurnTelemetry telemetry, WorkflowExecutionResult result) {
        if (telemetry == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", result.getWorkflowId());
        payload.put("exitReason", result.getExitReason() != null ? result.getExitReason().name() : null);
        payload.put("nodeCount", result.getExecutions() != null ? result.getExecutions().size() : 0);
        payload.put("durationMs", result.getDurationMs());
        payload.put("errorMessage", result.getErrorMessage());
        eventPublisher.publish(telemetry, ConversationStreamEventType.WORKFLOW_COMPLETED, payload);
    }

    /** 跨 AGENT 节点的 token 用量与工具调用累计器 */
    private static class UsageAccumulator {

        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private boolean hasUsage;
        private final List<ToolCallTelemetry> toolCalls = new ArrayList<>();

        void accumulate(ReactExecutionResult result) {
            if (result == null || result.getSteps() == null) {
                return;
            }
            for (ReactStep step : result.getSteps()) {
                if (step.getModelResponse() != null && step.getModelResponse().getUsage() != null) {
                    ChatUsageMetrics usage = step.getModelResponse().getUsage();
                    if (usage.getPromptTokens() != null) {
                        promptTokens += usage.getPromptTokens();
                        hasUsage = true;
                    }
                    if (usage.getCompletionTokens() != null) {
                        completionTokens += usage.getCompletionTokens();
                        hasUsage = true;
                    }
                    if (usage.getTotalTokens() != null) {
                        totalTokens += usage.getTotalTokens();
                        hasUsage = true;
                    }
                }
                if (step.getToolResults() != null) {
                    for (ReactToolResult toolResult : step.getToolResults()) {
                        toolCalls.add(toToolCallTelemetry(toolResult));
                    }
                }
            }
        }

        private ToolCallTelemetry toToolCallTelemetry(ReactToolResult toolResult) {
            return ToolCallTelemetry.builder()
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
                    .build();
        }

        private String truncate(String value, int maxLength) {
            if (value == null || value.length() <= maxLength) {
                return value;
            }
            return value.substring(0, maxLength);
        }

        List<ToolCallTelemetry> getToolCalls() {
            return toolCalls;
        }

        ChatUsageMetrics toMetrics() {
            if (!hasUsage) {
                return null;
            }
            return ChatUsageMetrics.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .build();
        }
    }

    /** 节点所有无条件出边的目标（并行扇出）；空则终止 */
    private List<String> parallelTargets(WorkflowNode node) {
        List<WorkflowEdge> edges = node.getOutgoingEdges();
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        return edges.stream()
                .filter(edge -> !edge.isConditional())
                .map(WorkflowEdge::getToNodeId)
                .toList();
    }

    /** 从执行记录提取下一节点列表（优先 routedToNodeIds） */
    private List<String> selectedNextNodeIds(WorkflowNodeExecution execution) {
        List<String> ids = execution.getRoutedToNodeIds();
        if (ids != null && !ids.isEmpty()) {
            return ids;
        }
        if (execution.getRoutedToNodeId() != null) {
            return List.of(execution.getRoutedToNodeId());
        }
        return List.of();
    }

    private WorkflowNodeOutcome outcome(WorkflowNode node, Object output, List<String> next) {
        List<String> ids = next == null ? List.of() : next;
        return WorkflowNodeOutcome.builder()
                .nodeId(node.getNodeId())
                .output(output)
                .routedToNodeId(ids.isEmpty() ? null : ids.get(0))
                .routedToNodeIds(ids)
                .build();
    }

    private String resolveSystemPrompt(AgentRuntime runtime) {
        AgentConfigModel config = runtime.getConfig();
        if (config != null && config.getAgent() != null && config.getAgent().getAgentDesc() != null) {
            String desc = config.getAgent().getAgentDesc().trim();
            return desc.isEmpty() ? null : desc;
        }
        return null;
    }

    /** 与 ChatServiceImpl.resolveReactPolicy 同构（Workflow AGENT 节点复用 Agent yml 的 ReAct 预算） */
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

    /** 预计算 Join 屏障所需到达分支数（nodeId -> 无条件入边数，≥2 才需要屏障） */
    private Map<String, Integer> computeJoinRequired(WorkflowDefinition definition) {
        Map<String, Integer> uncondIn = new LinkedHashMap<>();
        if (definition.getEdges() != null) {
            for (WorkflowEdge edge : definition.getEdges()) {
                if (!edge.isConditional()) {
                    uncondIn.merge(edge.getToNodeId(), 1, Integer::sum);
                }
            }
        }
        Map<String, Integer> joinRequired = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : uncondIn.entrySet()) {
            if (entry.getValue() >= 2) {
                joinRequired.put(entry.getKey(), entry.getValue());
            }
        }
        return joinRequired;
    }

    private WorkflowExecutionResult completed(ExecCtx ctx) {
        List<WorkflowNodeExecution> sorted = new ArrayList<>(ctx.executions);
        sorted.sort(Comparator.comparingInt(WorkflowNodeExecution::getIndex));
        Object finalOutput = computeFinalOutput(sorted);
        return WorkflowExecutionResult.builder()
                .workflowId(ctx.runtime.getDefinition().getId())
                .exitReason(WorkflowExitReason.COMPLETED)
                .executions(sorted)
                .finalOutput(finalOutput)
                .durationMs(durationMs(ctx.startedAt))
                .build();
    }

    private WorkflowExecutionResult failed(ExecCtx ctx, String errorMessage) {
        List<WorkflowNodeExecution> sorted = new ArrayList<>(ctx.executions);
        sorted.sort(Comparator.comparingInt(WorkflowNodeExecution::getIndex));
        return WorkflowExecutionResult.builder()
                .workflowId(ctx.runtime.getDefinition().getId())
                .exitReason(WorkflowExitReason.NODE_FAILED)
                .executions(sorted)
                .errorMessage(errorMessage)
                .durationMs(durationMs(ctx.startedAt))
                .build();
    }

    /**
     * finalOutput：取按执行序号排序后，最后一个终止节点（无出边）的输出。
     * 并行汇聚场景下，汇聚节点通常序号最大，自然成为终态输出；单列汇聚时亦成立。
     */
    private Object computeFinalOutput(List<WorkflowNodeExecution> sorted) {
        Object finalOutput = null;
        for (WorkflowNodeExecution execution : sorted) {
            boolean terminal = (execution.getRoutedToNodeIds() == null
                    || execution.getRoutedToNodeIds().isEmpty())
                    && execution.getRoutedToNodeId() == null;
            if (terminal && execution.getOutput() != null) {
                finalOutput = execution.getOutput();
            }
        }
        return finalOutput;
    }

    private long durationMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName() : message;
    }

}
