package com.vinist.domain.agent.model.workflow;

/**
 * Workflow 执行退出原因。
 */
public enum WorkflowExitReason {

    /** 从入口到达终止节点，正常完成 */
    COMPLETED,

    /** 某节点执行失败（含 AGENT 引擎异常、组件异常） */
    NODE_FAILED,

    /** CONDITION 组件返回的分支未命中任何出边 */
    ROUTE_NOT_MATCHED,

    /** 执行节点数超过安全上限（防御性，正常 DAG 不会触发） */
    MAX_NODES_EXCEEDED

}
