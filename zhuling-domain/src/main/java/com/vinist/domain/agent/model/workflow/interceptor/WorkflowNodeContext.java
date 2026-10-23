package com.vinist.domain.agent.model.workflow.interceptor;

import com.vinist.domain.agent.model.workflow.WorkflowNodeType;
import com.vinist.domain.agent.model.workflow.WorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Workflow 节点执行上下文（拦截器可见）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeContext {

    private String workflowId;

    private String nodeId;

    private String nodeName;

    private WorkflowNodeType nodeType;

    /** 执行序号（从 1 开始） */
    private int index;

    /** 当前黑板状态（只读语义，拦截器不应直接写入） */
    private WorkflowState state;

}
