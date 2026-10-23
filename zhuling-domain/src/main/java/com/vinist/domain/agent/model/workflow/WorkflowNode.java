package com.vinist.domain.agent.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Workflow 节点（编译后）。
 *
 * <p>设计约定（Phase 8 决策）：YAML 只描述共享状态、节点和边，
 * 复杂行为由显式注册的 Java 组件承担（见 {@code component} 字段）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    /** 节点 id（Workflow 内唯一） */
    private String nodeId;

    /** 节点展示名 */
    private String name;

    /** 节点类型 */
    private WorkflowNodeType type;

    /** AGENT 节点：引用的已注册 Agent id */
    private String agentId;

    /** CODE/TOOL/CONDITION 节点：显式注册的 Java 组件名 */
    private String component;

    /**
     * AGENT 节点：输入模板，支持 {@code ${key}} 占位符，
     * 解析时 nodeOutputs 优先于 variables（运行时由调度器渲染）。
     */
    private String inputTemplate;

    /** 节点输出写入黑板 nodeOutputs 的 key（可选） */
    private String outputKey;

    /** 以本节点为起点的出边 */
    private List<WorkflowEdge> outgoingEdges;

}
