package com.vinist.domain.agent.model.workflow.interceptor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Workflow 节点执行结果（拦截器可见）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeOutcome {

    private String nodeId;

    /** 节点输出（CONDITION 节点为命中的分支键） */
    private Object output;

    /** 路由到的下一节点 id；null 表示终止节点（顺序/条件分支时单值） */
    private String routedToNodeId;

    /** 并行扇出时的多个下一节点 id（顺序/条件分支时为空或仅含 routedToNodeId） */
    private List<String> routedToNodeIds;

}
