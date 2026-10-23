package com.vinist.domain.agent.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Workflow 一次完整执行的结果。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionResult {

    private String workflowId;

    private WorkflowExitReason exitReason;

    /** 全部节点执行记录（按执行顺序） */
    @Builder.Default
    private List<WorkflowNodeExecution> executions = List.of();

    /** 终态输出（最后一个非空节点输出） */
    private Object finalOutput;

    private String errorMessage;

    private long durationMs;

}
