package com.vinist.domain.design.framework.link.model1;

/**
 * @description 略规则责任链接口
 */
public interface ILogicLink<T, D, R> extends ILogicChainArmory<T, D, R> {

    R apply(T requestParameter, D dynamicContext) throws Exception;

}
