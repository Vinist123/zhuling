package com.vinist.domain.agent.model.workflow;

import com.vinist.domain.agent.model.AgentRuntime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Workflow 运行时（编译 + 引用解析后的产物）。
 *
 * <p>持有定义 DAG 与节点所需的已解析组件引用，调度器（批次二）据此执行；
 * 对外注册为 WORKFLOW 类型对话目标（批次三）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRuntime {

    private String workflowId;

    private WorkflowDefinition definition;

    /** AGENT 节点：nodeId -> 已解析的 AgentRuntime */
    private Map<String, AgentRuntime> agentNodes;

    /** CODE 节点：nodeId -> 已解析的代码组件 */
    private Map<String, IWorkflowCodeNode> codeNodes;

    /** CONDITION 节点：nodeId -> 已解析的条件组件 */
    private Map<String, IWorkflowCondition> conditionNodes;

}
