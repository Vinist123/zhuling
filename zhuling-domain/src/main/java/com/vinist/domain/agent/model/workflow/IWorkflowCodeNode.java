package com.vinist.domain.agent.model.workflow;

import reactor.core.publisher.Mono;

/**
 * Workflow 代码节点组件（显式注册的 Java 组件）。
 *
 * <p>实现类注册为 Spring Bean 后即可在 YAML 的 CODE 节点通过
 * {@code component: <beanName>} 引用。返回值（非 null 时）以 outputKey
 * 写入黑板 nodeOutputs。</p>
 */
@FunctionalInterface
public interface IWorkflowCodeNode {

    /**
     * 执行代码节点逻辑。
     *
     * @param state 当前黑板状态（可读取，如需写入请返回值而非直接改状态）
     * @return 节点输出（写入 outputKey；null 表示不写入）
     */
    Mono<Object> execute(WorkflowState state);

}
