package com.vinist.infrastructure.runtime.workflow;

import com.vinist.domain.agent.model.workflow.IWorkflowCodeNode;
import com.vinist.domain.agent.model.workflow.IWorkflowCondition;
import com.vinist.domain.agent.service.IWorkflowNodeRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Workflow 组件注册表实现。
 *
 * <p>开发者显式注册组件 = 声明为 Spring Bean（Bean 名即 YAML 中的 component 名）。
 * 启动时一次性收集全部 {@link IWorkflowCodeNode} / {@link IWorkflowCondition} Bean。</p>
 */
@Service
public class WorkflowNodeRegistryImpl implements IWorkflowNodeRegistry {

    private final Map<String, IWorkflowCodeNode> codeNodes;
    private final Map<String, IWorkflowCondition> conditions;

    public WorkflowNodeRegistryImpl(ApplicationContext applicationContext) {
        this.codeNodes = applicationContext.getBeansOfType(IWorkflowCodeNode.class);
        this.conditions = applicationContext.getBeansOfType(IWorkflowCondition.class);
    }

    @Override
    public IWorkflowCodeNode getCodeNode(String component) {
        return component == null ? null : codeNodes.get(component);
    }

    @Override
    public IWorkflowCondition getCondition(String component) {
        return component == null ? null : conditions.get(component);
    }

}
