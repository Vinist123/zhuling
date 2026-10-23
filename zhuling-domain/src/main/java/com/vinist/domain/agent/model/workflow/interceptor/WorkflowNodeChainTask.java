package com.vinist.domain.agent.model.workflow.interceptor;

import reactor.core.publisher.Mono;

/**
 * Workflow 节点本体任务：由调度器按节点类型装配（AGENT 走执行引擎、CODE 走组件、
 * CONDITION 走路由组件），拦截器链通过 {@code around} 包裹执行。
 */
@FunctionalInterface
public interface WorkflowNodeChainTask {

    Mono<WorkflowNodeOutcome> run(WorkflowNodeContext context);

}
