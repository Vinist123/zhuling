package com.vinist.domain.agent.model.workflow.interceptor;

import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workflow 节点事件发布拦截器（批次三）。
 *
 * <p>在节点生命周期边界发布 SSE 实时事件：
 * {@code workflow.node.started} / {@code workflow.node.completed}，
 * 内部 AGENT 节点的 {@code reasoning.delta} / {@code agent.step.*} 事件
 * 由 ReAct 内核经同一 telemetry 通道发布，前端按时序交错展示。</p>
 *
 * <p>每次 Workflow 执行创建新实例并绑定本轮 telemetry（telemetry 为空时不发布）。</p>
 */
public class WorkflowEventPublishingInterceptor implements IWorkflowNodeInterceptor {

    private static final int OUTPUT_PREVIEW_MAX_LENGTH = 200;

    private final IConversationStreamEventPublisher eventPublisher;
    private final TurnTelemetry telemetry;
    private final Map<Integer, Instant> nodeStartTimes = new ConcurrentHashMap<>();

    public WorkflowEventPublishingInterceptor(IConversationStreamEventPublisher eventPublisher,
                                              TurnTelemetry telemetry) {
        this.eventPublisher = eventPublisher;
        this.telemetry = telemetry;
    }

    @Override
    public Mono<Void> beforeNode(WorkflowNodeContext context) {
        if (telemetry == null || context == null) {
            return Mono.empty();
        }
        nodeStartTimes.put(context.getIndex(), Instant.now());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", context.getWorkflowId());
        payload.put("index", context.getIndex());
        payload.put("nodeId", context.getNodeId());
        payload.put("nodeName", context.getNodeName());
        payload.put("nodeType", context.getNodeType() != null ? context.getNodeType().name() : null);
        eventPublisher.publish(telemetry, ConversationStreamEventType.WORKFLOW_NODE_STARTED, payload);
        return Mono.empty();
    }

    @Override
    public Mono<Void> afterNode(WorkflowNodeContext context, WorkflowNodeOutcome outcome) {
        if (telemetry == null || context == null) {
            return Mono.empty();
        }
        Instant startedAt = nodeStartTimes.get(context.getIndex());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", context.getWorkflowId());
        payload.put("index", context.getIndex());
        payload.put("nodeId", context.getNodeId());
        payload.put("nodeName", context.getNodeName());
        payload.put("nodeType", context.getNodeType() != null ? context.getNodeType().name() : null);
        payload.put("routedToNodeId", outcome != null ? outcome.getRoutedToNodeId() : null);
        payload.put("routedToNodeIds", outcome != null ? outcome.getRoutedToNodeIds() : null);
        payload.put("outputPreview", preview(outcome != null ? outcome.getOutput() : null));
        payload.put("durationMs", startedAt != null
                ? java.time.Duration.between(startedAt, Instant.now()).toMillis() : null);
        eventPublisher.publish(telemetry, ConversationStreamEventType.WORKFLOW_NODE_COMPLETED, payload);
        return Mono.empty();
    }

    @Override
    public Mono<IllegalStateException> onFailure(WorkflowNodeContext context, Throwable error) {
        if (telemetry == null || context == null) {
            return Mono.empty();
        }
        Instant startedAt = nodeStartTimes.get(context.getIndex());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", context.getWorkflowId());
        payload.put("index", context.getIndex());
        payload.put("nodeId", context.getNodeId());
        payload.put("nodeName", context.getNodeName());
        payload.put("nodeType", context.getNodeType() != null ? context.getNodeType().name() : null);
        payload.put("success", false);
        payload.put("errorMessage", error != null && error.getMessage() != null
                ? error.getMessage() : "未知错误");
        payload.put("durationMs", startedAt != null
                ? java.time.Duration.between(startedAt, Instant.now()).toMillis() : null);
        eventPublisher.publish(telemetry, ConversationStreamEventType.WORKFLOW_NODE_COMPLETED, payload);
        return Mono.empty();
    }

    private String preview(Object output) {
        if (output == null) {
            return null;
        }
        String text = String.valueOf(output);
        return text.length() <= OUTPUT_PREVIEW_MAX_LENGTH ? text
                : text.substring(0, OUTPUT_PREVIEW_MAX_LENGTH) + "…";
    }

}
