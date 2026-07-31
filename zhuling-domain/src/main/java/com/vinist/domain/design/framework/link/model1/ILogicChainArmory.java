package com.vinist.domain.design.framework.link.model1;

/**
 * @title: ILogicChainArmory
 * @description: <责任链装配>
 * @author: hd
 * @date: 2025/7/9 14:04
 */
public interface ILogicChainArmory<T, D, R> {

    ILogicLink<T, D, R> next();

    ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> next);

}
