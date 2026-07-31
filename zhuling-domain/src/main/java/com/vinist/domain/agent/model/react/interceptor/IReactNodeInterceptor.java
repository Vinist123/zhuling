package com.vinist.domain.agent.model.react.interceptor;

import com.vinist.domain.design.framework.link.model2.handler.ILogicHandler;
import reactor.core.publisher.Mono;

/**
 * ReAct 节点生命周期拦截器。
 *
 * <p>基于责任链 {@link ILogicHandler} 实现，执行器通过 {@code around} 将节点
 * （MODEL 推理 / TOOL 工具）包裹在拦截器链中，实现节点级观测、错误归一化与安全策略。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>节点本体由 {@code nodeTask} 提供（模型调用或工具执行），拦截器不直接实现节点逻辑；</li>
 *     <li>{@code beforeNode}/{@code afterNode}/{@code onFailure} 默认空实现，子类按需覆盖；</li>
 *     <li>{@code normalize} 将异常映射为 {@link ReactNodeException}，默认按类别分类；</li>
 *     <li>所有方法返回 {@code Mono}，与 ReAct 响应式主循环一致。</li>
 * </ul>
 */
public interface IReactNodeInterceptor extends ILogicHandler<ReactNodeContext, ReactNodeChainTask, ReactNodeOutcome> {

    /**
     * 节点进入前回调（观测 / 安全预检 / 审计）。
     *
     * @param context 节点上下文
     */
    default Mono<Void> beforeNode(ReactNodeContext context) {
        return Mono.empty();
    }

    /**
     * 节点成功完成后回调（观测 / 埋点补充）。
     *
     * @param context 节点上下文
     * @param outcome 节点执行结果
     */
    default Mono<Void> afterNode(ReactNodeContext context, ReactNodeOutcome outcome) {
        return Mono.empty();
    }

    /**
     * 节点失败后回调（错误归一化 / 告警 / 安全策略）。
     *
     * <p>默认返回 {@code Mono.empty()}（不拦截，交由后续拦截器或链兜底归一化）；
     * 期望承担归一化的拦截器（如 {@code ErrorNormalizeReactNodeInterceptor}）覆盖此方法。</p>
     *
     * @param context 节点上下文
     * @param error   原始异常
     * @return 归一化后的异常；返回空表示本拦截器不处理，链会继续询问下一个
     */
    default Mono<ReactNodeException> onFailure(ReactNodeContext context, Throwable error) {
        return Mono.empty();
    }

    /**
     * 异常归一化：将原始异常映射为带分类的 {@link ReactNodeException}。
     *
     * @param context 节点上下文
     * @param error   原始异常
     * @return 归一化异常
     */
    default ReactNodeException normalize(ReactNodeContext context, Throwable error) {
        ReactNodeErrorCategory category;
        if (error instanceof java.util.concurrent.TimeoutException) {
            category = ReactNodeErrorCategory.TIMEOUT;
        } else if (context != null && context.getNodeType() == ReactNodeContext.NodeType.TOOL) {
            category = ReactNodeErrorCategory.TOOL_ERROR;
        } else {
            category = ReactNodeErrorCategory.MODEL_ERROR;
        }
        return ReactNodeException.of(category, context,
                "节点执行失败: " + (error != null ? error.getMessage() : "未知错误"), error);
    }

    /**
     * 责任链入口：包裹节点执行。
     *
     * <p>依次执行 {@code beforeNode} → 节点本体 → {@code afterNode}；
     * 节点或 {@code beforeNode/afterNode} 抛异常时走 {@code onFailure} 归一化后重新抛出。</p>
     *
     * @param context   节点上下文（作为 request 参数，符合 {@link ILogicHandler} 契约）
     * @param nodeTask  节点本体（作为 dynamicContext 参数，符合 {@link ILogicHandler} 契约）
     * @return 节点结果（永远非空；失败路径已将异常归一化并抛出）
     */
    @Override
    default ReactNodeOutcome apply(ReactNodeContext context, ReactNodeChainTask nodeTask) {
        throw new UnsupportedOperationException(
                "IReactNodeInterceptor 必须经由 ReactNodeInterceptorChain.around 异步执行，不可同步直接调用");
    }

}
