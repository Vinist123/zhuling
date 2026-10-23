package com.vinist.domain.agent.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Workflow 定义（由 YAML 配置编译产出的受控 DAG）。
 *
 * <p>编译保证以下不变式（详见 WorkflowCompiler 校验规则）：
 * <ul>
 *   <li>entry 节点存在，且所有节点从 entry 可达</li>
 *   <li>图为有向无环（Kahn 拓扑排序校验）</li>
 *   <li>非 CONDITION 节点出边全部无条件，可多条（并行扇出）；CONDITION 节点出边全部带互不重复的 when</li>
 *   <li>汇聚（Join）节点：≥2 条无条件入边，运行时屏障等待所有并行分支</li>
 * </ul></p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {

    /** Workflow id（全局唯一，注册为对话目标时使用） */
    private String id;

    /** 展示名 */
    private String name;

    /** 描述 */
    private String description;

    /** 入口节点 id */
    private String entryNodeId;

    /** 初始共享变量（黑板 variables 的初始值） */
    private Map<String, Object> initialState;

    /** 全部节点（nodeId -> node） */
    private Map<String, WorkflowNode> nodes;

    /** 全部边 */
    private List<WorkflowEdge> edges;

    public WorkflowNode getEntryNode() {
        return nodes.get(entryNodeId);
    }

    public WorkflowNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /** 是否为终止节点（无出边） */
    public boolean isTerminal(String nodeId) {
        WorkflowNode node = nodes.get(nodeId);
        return node == null || node.getOutgoingEdges() == null
                || node.getOutgoingEdges().isEmpty();
    }

}
