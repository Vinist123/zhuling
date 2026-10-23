package com.vinist.domain.agent.model.workflow.interceptor;

import com.vinist.domain.design.framework.link.model2.LinkArmory;
import com.vinist.domain.design.framework.link.model2.chain.BusinessLinkedList;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Workflow 节点拦截器链。
 *
 * <p>与 {@code ReactNodeInterceptorChain} 同构：基于自研 {@link BusinessLinkedList}
 * 责任链装配多个 {@link IWorkflowNodeInterceptor}，提供响应式 {@code around}
 * 方法包裹单个节点的执行。执行顺序：</p>
 * <ol>
 *     <li>依次调用所有拦截器的 {@code beforeNode}；</li>
 *     <li>执行节点本体；</li>
 *     <li>成功时依次调用 {@code afterNode}；</li>
 *     <li>失败时依次询问 {@code onFailure} 归一化，取第一个非空结果重新抛出，
 *         都为空则兜底归一化为带节点上下文的 {@link IllegalStateException}。</li>
 * </ol>
 */
public class WorkflowNodeInterceptorChain {

    private static final String ILLEGAL_STATE_TEMPLATE = "Workflow 节点执行失败: workflow=%s, node=%s, reason=%s";

    private final List<IWorkflowNodeInterceptor> interceptors;

    /** 基于责任链组件装配，保留顺序，供框架边界说明与后续策略路由复用 */
    @SuppressWarnings("unused")
    private final BusinessLinkedList<WorkflowNodeContext, WorkflowNodeChainTask, WorkflowNodeOutcome> linkList;

    public WorkflowNodeInterceptorChain(List<IWorkflowNodeInterceptor> interceptors) {
        this.interceptors = interceptors == null ? List.of()
                : List.copyOf(interceptors);
        if (this.interceptors.isEmpty()) {
            this.linkList = new BusinessLinkedList<>("workflow-node-empty-chain");
        } else {
            ILogicHandlerAdapter[] adapters = this.interceptors.stream()
                    .map(ILogicHandlerAdapter::new)
                    .toArray(ILogicHandlerAdapter[]::new);
            LinkArmory<WorkflowNodeContext, WorkflowNodeChainTask, WorkflowNodeOutcome> armory =
                    new LinkArmory<>("workflow-node-chain", adapters);
            this.linkList = armory.getLogicLink();
        }
    }

    public WorkflowNodeInterceptorChain(IWorkflowNodeInterceptor... interceptors) {
        this(Arrays.asList(interceptors));
    }

    /** 包裹节点执行，依次触发拦截器生命周期 */
    public Mono<WorkflowNodeOutcome> around(WorkflowNodeContext context, WorkflowNodeChainTask nodeTask) {
        return Mono.defer(() -> beforeAll(context)
                .then(runNode(context, nodeTask))
                .flatMap(outcome -> afterAll(context, outcome).thenReturn(outcome))
                .onErrorResume(error -> onFailureAll(context, error)
                        .flatMap(normalized -> Mono.<WorkflowNodeOutcome>error(normalized))));
    }

    private Mono<Void> beforeAll(WorkflowNodeContext context) {
        return Mono.defer(() -> {
            Mono<Void> chain = Mono.empty();
            for (IWorkflowNodeInterceptor interceptor : interceptors) {
                chain = chain.then(interceptor.beforeNode(context));
            }
            return chain;
        });
    }

    private Mono<WorkflowNodeOutcome> runNode(WorkflowNodeContext context, WorkflowNodeChainTask nodeTask) {
        return Mono.defer(() -> nodeTask.run(context))
                .flatMap(outcome -> {
                    if (outcome == null) {
                        return Mono.error(new IllegalStateException(
                                "Workflow 节点返回空结果: " + context.getNodeId()));
                    }
                    return Mono.just(outcome);
                });
    }

    private Mono<Void> afterAll(WorkflowNodeContext context, WorkflowNodeOutcome outcome) {
        Mono<Void> chain = Mono.empty();
        for (IWorkflowNodeInterceptor interceptor : interceptors) {
            chain = chain.then(interceptor.afterNode(context, outcome));
        }
        return chain;
    }

    private Mono<IllegalStateException> onFailureAll(WorkflowNodeContext context, Throwable error) {
        Mono<IllegalStateException> chain = Mono.empty();
        for (IWorkflowNodeInterceptor interceptor : interceptors) {
            chain = chain.switchIfEmpty(interceptor.onFailure(context, error));
        }
        String reason = error != null && error.getMessage() != null ? error.getMessage() : "未知错误";
        return chain.switchIfEmpty(Mono.fromCallable(() -> new IllegalStateException(
                String.format(ILLEGAL_STATE_TEMPLATE, context.getWorkflowId(),
                        context.getNodeId(), reason), error)));
    }

    /**
     * {@link com.vinist.domain.design.framework.link.model2.handler.ILogicHandler} 适配器：
     * 本链不使用责任链 short-circuit 语义，仅作为顺序占位节点（与 ReAct 拦截器链一致）。
     */
    private static class ILogicHandlerAdapter
            implements com.vinist.domain.design.framework.link.model2.handler.ILogicHandler<
            WorkflowNodeContext, WorkflowNodeChainTask, WorkflowNodeOutcome> {

        private final IWorkflowNodeInterceptor delegate;

        private ILogicHandlerAdapter(IWorkflowNodeInterceptor delegate) {
            this.delegate = delegate;
        }

        @Override
        public WorkflowNodeOutcome apply(WorkflowNodeContext requestParameter,
                                         WorkflowNodeChainTask dynamicContext) {
            return null;
        }
    }

}
