package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.workflow.IWorkflowCodeNode;
import com.vinist.domain.agent.model.workflow.IWorkflowCondition;

/**
 * Workflow 组件注册表。
 *
 * <p>开发者通过显式注册组件（Spring Bean）修改调用流程：
 * 实现类声明为 Bean 后，即可在 Workflow YAML 中按组件名引用。</p>
 */
public interface IWorkflowNodeRegistry {

    /** 按组件名获取代码节点组件；不存在返回 null */
    IWorkflowCodeNode getCodeNode(String component);

    /** 按组件名获取条件路由组件；不存在返回 null */
    IWorkflowCondition getCondition(String component);

}
