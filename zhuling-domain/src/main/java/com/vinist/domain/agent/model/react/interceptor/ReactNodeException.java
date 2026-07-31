package com.vinist.domain.agent.model.react.interceptor;

import lombok.Getter;

/**
 * ReAct 节点归一化异常。
 *
 * <p>拦截器链 {@code onFailure} 阶段将原始异常包装为带 {@link ReactNodeErrorCategory}
 * 与 {@link ReactNodeContext} 的稳定异常，供执行器统一处理退出原因与安全策略。</p>
 */
@Getter
public class ReactNodeException extends RuntimeException {

    private final ReactNodeErrorCategory category;

    private final ReactNodeContext context;

    public ReactNodeException(ReactNodeErrorCategory category,
                              ReactNodeContext context,
                              String message,
                              Throwable cause) {
        super(message, cause);
        this.category = category;
        this.context = context;
    }

    public static ReactNodeException of(ReactNodeErrorCategory category,
                                        ReactNodeContext context,
                                        String message,
                                        Throwable cause) {
        return new ReactNodeException(category, context, message, cause);
    }

}
