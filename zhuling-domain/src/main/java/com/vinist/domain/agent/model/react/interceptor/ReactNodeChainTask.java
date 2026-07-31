package com.vinist.domain.agent.model.react.interceptor;

import reactor.core.publisher.Mono;

/**
 * ReAct 节点本体任务。
 *
 * <p>由执行器提供：MODEL 节点执行模型调用、TOOL 节点执行工具集合。
 * 返回 {@link ReactNodeOutcome} 的 {@link Mono}，供拦截器链 {@code afterNode} 处理。</p>
 */
@FunctionalInterface
public interface ReactNodeChainTask {

    Mono<ReactNodeOutcome> run(ReactNodeContext context);

}
