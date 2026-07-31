package com.vinist.domain.design.framework.link.model2.chain;


import com.vinist.domain.design.framework.link.model2.handler.ILogicHandler;

/**
 * @title: BusinessLinkedList
 * @description: <业务链路>
 * @author: hd
 * @date: 2025/7/9 14:11
 */
public class BusinessLinkedList<T, D, R> extends LinkedList<ILogicHandler<T, D, R>> implements ILogicHandler<T, D, R> {

    public BusinessLinkedList(String name) {
        super(name);
    }

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        Node<ILogicHandler<T, D, R>> current = this.first;
        do {
            ILogicHandler<T, D, R> item = current.item;
            R apply = item.apply(requestParameter, dynamicContext);
            if (null != apply) return apply;

            current = current.next;
        } while (null != current);

        return null;
    }

}
