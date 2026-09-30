package com.vinist.domain.design.framework.tree;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @title: AbstractMultiThreadStrategyRouter
 * @description: <异步资源加载策略>
 * @author: hd
 * @date: 2025/6/5 14:27
 */
public abstract class AbstractMultiThreadStrategyRouter<T, D, R> implements StrategyMapper<T, D, R>, StrategyHandler<T, D, R> {
    @Getter
    @Setter
    protected StrategyHandler<T, D, R> defaultStrategyHandler = StrategyHandler.DEFAULT;

    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> strategyHandler = get(requestParameter, dynamicContext);
        if (null != strategyHandler) return strategyHandler.apply(requestParameter, dynamicContext);
        return defaultStrategyHandler.apply(requestParameter, dynamicContext);
    }

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        // 异步执行
        multiThread(requestParameter, dynamicContext);
        // 业务流程
        return doApply(requestParameter, dynamicContext);
    }


    /**
    *@Description: 异步加载
    *@Param: [requestParameter, dynamicContext]
    *@return: void
    *@Author: hd
    *@Date: 2025/6/5 14:33
    **/
    protected abstract void multiThread(T requestParameter, D dynamicContext) throws ExecutionException, InterruptedException, TimeoutException;

    /**
    *@Description: 业务流程受理
    *@Param: [requestParameter, dynamicContext]
    *@return: R
    *@Author: hd
    *@Date: 2025/6/5 14:33
    **/
    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;


}
