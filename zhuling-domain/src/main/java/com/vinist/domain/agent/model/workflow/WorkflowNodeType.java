package com.vinist.domain.agent.model.workflow;

/**
 * Workflow 节点类型。
 *
 * <p>第一版（Phase 8 批次一）落地 AGENT / CODE / CONDITION 三类；
 * TOOL 保留枚举位，编译时显式拒绝，待后续批次启用。</p>
 */
public enum WorkflowNodeType {

    /** 引用已注册 Agent 的节点，由 Agent 以独立对话轮次执行 */
    AGENT,

    /** 显式注册的 Java 代码组件节点 */
    CODE,

    /** 显式注册的工具组件节点（第一版暂未支持，编译时报错） */
    TOOL,

    /** 条件路由节点，由注册的 Java 条件组件决定下一跳 */
    CONDITION

}
