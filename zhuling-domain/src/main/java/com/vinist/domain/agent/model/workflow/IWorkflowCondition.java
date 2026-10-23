package com.vinist.domain.agent.model.workflow;

/**
 * Workflow 条件路由组件（显式注册的 Java 组件）。
 *
 * <p>实现类注册为 Spring Bean 后即可在 YAML 的 CONDITION 节点通过
 * {@code component: <beanName>} 引用。返回值与出边的 {@code when} 精确匹配。</p>
 */
@FunctionalInterface
public interface IWorkflowCondition {

    /**
     * 根据黑板状态决定路由分支。
     *
     * @param state 当前黑板状态（可读不可写）
     * @return 分支键，必须与 CONDITION 节点某条出边的 when 值匹配
     */
    String route(WorkflowState state);

}
