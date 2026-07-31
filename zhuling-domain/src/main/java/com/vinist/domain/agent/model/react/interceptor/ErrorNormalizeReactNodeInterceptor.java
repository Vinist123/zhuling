package com.vinist.domain.agent.model.react.interceptor;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * ReAct 节点错误归一化拦截器。
 *
 * <p>在 {@code onFailure} 阶段将原始异常转换为带 {@link ReactNodeErrorCategory} 的
 * {@link ReactNodeException}，并输出告警日志；{@code normalize} 已在接口中提供默认分类
 * （TIMEOUT / TOOL_ERROR / MODEL_ERROR），此处仅补充日志与消息标准化。</p>
 */
@Slf4j
public class ErrorNormalizeReactNodeInterceptor implements IReactNodeInterceptor {

    @Override
    public Mono<ReactNodeException> onFailure(ReactNodeContext context, Throwable error) {
        ReactNodeException normalized = normalize(context, error);
        log.warn("ReAct 节点失败已归一化: category={}, type={}, step={}, message={}",
                normalized.getCategory(),
                context != null ? context.getNodeType() : null,
                context != null ? context.getStepIndex() : null,
                normalized.getMessage());
        return Mono.just(normalized);
    }

}
