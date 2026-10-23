package com.vinist.domain.agent.model.telemetry;

import com.vinist.domain.agent.model.workflow.WorkflowNodeExecution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Workflow 执行过程级观测摘要（仅当本轮走了 Workflow 调度时非空）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowProcessTelemetry {

    private String workflowId;

    private String exitReason;

    private int nodeCount;

    private long durationMs;

    private String errorMessage;

    private List<NodeSummary> nodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeSummary {

        private int index;

        private String nodeId;

        private String nodeName;

        private String nodeType;

        private String outputPreview;

        private String routedToNodeId;

        private List<String> routedToNodeIds;

        private Long durationMs;

        private Boolean success;

        private String error;

    }

    /** 从调度器执行记录构建摘要（输出截断，避免 metadata 膨胀） */
    public static WorkflowProcessTelemetry from(String workflowId,
                                                String exitReason,
                                                long durationMs,
                                                String errorMessage,
                                                List<WorkflowNodeExecution> executions) {
        List<NodeSummary> summaries = executions == null ? List.of()
                : executions.stream().map(execution -> NodeSummary.builder()
                        .index(execution.getIndex())
                        .nodeId(execution.getNodeId())
                        .nodeName(execution.getNodeName())
                        .nodeType(execution.getType() != null ? execution.getType().name() : null)
                        .outputPreview(preview(execution.getOutput()))
                        .routedToNodeId(execution.getRoutedToNodeId())
                        .routedToNodeIds(execution.getRoutedToNodeIds())
                        .durationMs(execution.getDurationMs())
                        .success(execution.isSuccess())
                        .error(preview(execution.getError()))
                        .build())
                .toList();
        return WorkflowProcessTelemetry.builder()
                .workflowId(workflowId)
                .exitReason(exitReason)
                .nodeCount(summaries.size())
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .nodes(summaries)
                .build();
    }

    private static String preview(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }

}
