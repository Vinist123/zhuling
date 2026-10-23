package com.vinist.domain.agent.model.workflow.interceptor;

import com.vinist.domain.design.framework.link.model2.handler.ILogicHandler;
import reactor.core.publisher.Mono;

/**
 * Workflow 节点生命周期拦截器。
 *
 * <p>基于责任链 {@link ILogicHandler}（经 {@code LinkArmory} 装配进
 * {@code BusinessLinkedList}），调度器通过 {@code around} 将节点执行包裹在
 * 拦截器链中，实现节点级观测与错误归一化（与 ReAct 节点拦截器同构）。</p>
 */
public interface IWorkflowNodeInterceptor
        extends ILogicHandler<WorkflowNodeContext, WorkflowNodeChainTask, WorkflowNodeOutcome> {

    /** 节点进入前回调（观测 / 安全预检） */
    default Mono<Void> beforeNode(WorkflowNodeContext context) {
        return Mono.empty();
    }

    /** 节点成功完成后回调（观测 / 埋点补充） */
    default Mono<Void> afterNode(WorkflowNodeContext context, WorkflowNodeOutcome outcome) {
        return Mono.empty();
    }

    /**
     * 节点失败后回调（错误归一化 / 告警）。
     *
     * <p>返回 {@code Mono.empty()} 表示本拦截器不处理，链继续询问下一个；
     * 全部为空时链兜底归一化为带节点上下文的 {@link IllegalStateException}。</p>
     */
    default Mono<IllegalStateException> onFailure(WorkflowNodeContext context, Throwable error) {
        return Mono.empty();
    }

    @Override
    default WorkflowNodeOutcome apply(WorkflowNodeContext requestParameter,
                                       WorkflowNodeChainTask dynamicContext) {
        throw new UnsupportedOperationException(
                "IWorkflowNodeInterceptor 必须经由 WorkflowNodeInterceptorChain.around 异步执行，不可同步直接调用");
    }

}
