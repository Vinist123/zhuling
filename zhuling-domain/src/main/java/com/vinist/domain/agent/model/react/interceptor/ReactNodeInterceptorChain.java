package com.vinist.domain.agent.model.react.interceptor;

import com.vinist.domain.design.framework.link.model2.LinkArmory;
import com.vinist.domain.design.framework.link.model2.chain.BusinessLinkedList;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * ReAct 节点拦截器链。
 *
 * <p>基于自研 {@link BusinessLinkedList} 责任链装配多个 {@link IReactNodeInterceptor}，
 * 提供响应式的 {@code around} 方法包裹单个节点（MODEL / TOOL）的执行。</p>
 *
 * <p>执行顺序：</p>
 * <ol>
 *     <li>依次调用所有拦截器的 {@code beforeNode}；</li>
 *     <li>执行节点本体 {@code nodeTask.run()}；</li>
 *     <li>成功时依次调用所有拦截器的 {@code afterNode}；</li>
 *     <li>失败时依次调用所有拦截器的 {@code onFailure} 归一化，取第一个非空归一化结果重新抛出。</li>
 * </ol>
 */
public class ReactNodeInterceptorChain {

    private final List<IReactNodeInterceptor> interceptors;

    /** 基于责任链组件装配，保留顺序，供框架边界说明与后续策略路由复用 */
    @SuppressWarnings("unused")
    private final BusinessLinkedList<ReactNodeContext, ReactNodeChainTask, ReactNodeOutcome> linkList;

    public ReactNodeInterceptorChain(List<IReactNodeInterceptor> interceptors) {
        this.interceptors = interceptors == null ? List.of()
                : List.copyOf(interceptors);
        if (this.interceptors.isEmpty()) {
            this.linkList = new BusinessLinkedList<>("react-node-empty-chain");
        } else {
            ILogicHandlerAdapter[] adapters = this.interceptors.stream()
                    .map(ILogicHandlerAdapter::new)
                    .toArray(ILogicHandlerAdapter[]::new);
            LinkArmory<ReactNodeContext, ReactNodeChainTask, ReactNodeOutcome> armory =
                    new LinkArmory<>("react-node-chain", adapters);
            this.linkList = armory.getLogicLink();
        }
    }

    public ReactNodeInterceptorChain(IReactNodeInterceptor... interceptors) {
        this(Arrays.asList(interceptors));
    }

    /**
     * 包裹节点执行，依次触发拦截器生命周期。
     *
     * @param context  节点上下文
     * @param nodeTask 节点本体任务（模型调用或工具执行）
     * @return 节点结果 Mono
     */
    public Mono<ReactNodeOutcome> around(ReactNodeContext context, ReactNodeChainTask nodeTask) {
        return Mono.defer(() -> beforeAll(context)
                .then(runNode(context, nodeTask))
                .flatMap(outcome -> afterAll(context, outcome).thenReturn(outcome))
                .onErrorResume(error -> onFailureAll(context, error)
                        .flatMap(normalized -> Mono.<ReactNodeOutcome>error(normalized))));
    }

    private Mono<Void> beforeAll(ReactNodeContext context) {
        return Mono.defer(() -> {
            // beforeNode 无状态传递需求，逐个执行（顺序本身不影响观测）
            Mono<Void> chain = Mono.empty();
            for (IReactNodeInterceptor interceptor : interceptors) {
                chain = chain.then(interceptor.beforeNode(context));
            }
            return chain;
        });
    }

    private Mono<ReactNodeOutcome> runNode(ReactNodeContext context, ReactNodeChainTask nodeTask) {
        return Mono.defer(() -> nodeTask.run(context))
                .flatMap(outcome -> {
                    if (outcome == null) {
                        return Mono.error(new IllegalStateException("节点返回空结果: " + context.getNodeType()));
                    }
                    return Mono.just(outcome);
                });
    }

    private Mono<Void> afterAll(ReactNodeContext context, ReactNodeOutcome outcome) {
        Mono<Void> chain = Mono.empty();
        for (IReactNodeInterceptor interceptor : interceptors) {
            chain = chain.then(interceptor.afterNode(context, outcome));
        }
        return chain;
    }

    private Mono<ReactNodeException> onFailureAll(ReactNodeContext context, Throwable error) {
        // 依次询问拦截器归一化，取第一个非空结果；都为空则兜底生成 INTERNAL
        Mono<ReactNodeException> chain = Mono.empty();
        for (IReactNodeInterceptor interceptor : interceptors) {
            chain = chain.switchIfEmpty(interceptor.onFailure(context, error));
        }
        return chain.switchIfEmpty(Mono.fromCallable(() ->
                ReactNodeException.of(ReactNodeErrorCategory.INTERNAL, context,
                        "节点执行失败(未分类): " + (error != null ? error.getMessage() : "未知错误"), error)));
    }

    /**
     * {@link ILogicHandler} 适配器：将 {@link IReactNodeInterceptor} 适配为责任链节点。
     *
     * <p>本拦截器链不使用责任链的 short-circuit 语义（不需要首个非空返回值），
     * 而是显式顺序执行 before/after/onFailure，因此适配器 {@code apply} 永不返回非空，
     * 仅作为占位节点承载顺序。</p>
     */
    private static class ILogicHandlerAdapter
            implements com.vinist.domain.design.framework.link.model2.handler.ILogicHandler<
            ReactNodeContext, ReactNodeChainTask, ReactNodeOutcome> {

        private final IReactNodeInterceptor delegate;

        private ILogicHandlerAdapter(IReactNodeInterceptor delegate) {
            this.delegate = delegate;
        }

        private IReactNodeInterceptor getDelegate() {
            return delegate;
        }

        @Override
        public ReactNodeOutcome apply(ReactNodeContext requestParameter, ReactNodeChainTask dynamicContext) {
            // 占位：实际 before/after/onFailure 由 ReactNodeInterceptorChain 显式驱动
            return null;
        }
    }

}
