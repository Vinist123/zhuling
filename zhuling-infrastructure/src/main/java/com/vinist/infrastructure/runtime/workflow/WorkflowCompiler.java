package com.vinist.infrastructure.runtime.workflow;

import com.vinist.domain.agent.model.workflow.WorkflowConfigModel;
import com.vinist.domain.agent.model.workflow.WorkflowDefinition;
import com.vinist.domain.agent.model.workflow.WorkflowEdge;
import com.vinist.domain.agent.model.workflow.WorkflowNode;
import com.vinist.domain.agent.model.workflow.WorkflowNodeType;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Workflow 编译器：YAML 配置 → 受控 DAG 定义。
 *
 * <p>校验规则（任一失败即抛出 {@link IllegalStateException}，启动 fail-fast）：
 * <ol>
 *   <li>id/name/entry/nodes 非空；节点 id 唯一非空；entry 节点存在</li>
 *   <li>type 必须是 AGENT/CODE/TOOL/CONDITION 之一；TOOL 第一版暂未支持，显式拒绝</li>
 *   <li>AGENT 节点必须声明 agent-id；CODE/CONDITION 节点必须声明 component</li>
 *   <li>边的 from/to 必须指向已存在节点；不允许自环</li>
 *   <li>非 CONDITION 节点出边必须全部无条件（可多条=并行扇出）；CONDITION 节点出边必须全部带唯一 when</li>
 *   <li>汇聚节点（≥2 条无条件入边）作为 Join 屏障，禁止同时接收并行分支与条件分支</li>
 *   <li>图为有向无环（Kahn 拓扑排序校验）</li>
 *   <li>所有节点从 entry 可达（拒绝孤儿节点）</li>
 * </ol></p>
 */
@Component
public class WorkflowCompiler {

    public WorkflowDefinition compile(WorkflowConfigModel config, String resourceName) {
        validateBasics(config, resourceName);
        String context = "workflow=" + config.getId() + " (" + resourceName + ")";

        Map<String, WorkflowNode> nodes = compileNodes(config, context);
        List<WorkflowEdge> edges = compileEdges(config, nodes, context);
        validateOutgoingEdgeRules(nodes, context);
        validateJoinRules(nodes, context);
        validateAcyclic(nodes, context);
        validateReachable(config.getEntry(), nodes, context);

        return WorkflowDefinition.builder()
                .id(config.getId())
                .name(config.getName())
                .description(config.getDescription())
                .entryNodeId(config.getEntry())
                .initialState(config.getState() != null ? config.getState() : Map.of())
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private void validateBasics(WorkflowConfigModel config, String resourceName) {
        if (config == null || isBlank(config.getId())) {
            throw new IllegalStateException("Workflow 配置缺少 id: " + resourceName);
        }
        if (isBlank(config.getName())) {
            throw new IllegalStateException("Workflow 配置缺少 name: " + config.getId());
        }
        if (isBlank(config.getEntry())) {
            throw new IllegalStateException("Workflow 配置缺少 entry: " + config.getId());
        }
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new IllegalStateException("Workflow 配置缺少 nodes: " + config.getId());
        }
    }

    private Map<String, WorkflowNode> compileNodes(WorkflowConfigModel config, String context) {
        Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
        for (WorkflowConfigModel.NodeConfig nodeConfig : config.getNodes()) {
            if (nodeConfig == null || isBlank(nodeConfig.getId())) {
                throw new IllegalStateException("Workflow 节点缺少 id: " + context);
            }
            if (nodes.putIfAbsent(nodeConfig.getId(), toNode(nodeConfig, context)) != null) {
                throw new IllegalStateException("Workflow 存在重复节点 id: " + nodeConfig.getId()
                        + "，" + context);
            }
        }
        if (!nodes.containsKey(config.getEntry())) {
            throw new IllegalStateException("entry 节点不存在: " + config.getEntry()
                    + "，" + context);
        }
        return nodes;
    }

    private WorkflowNode toNode(WorkflowConfigModel.NodeConfig nodeConfig, String context) {
        if (nodeConfig.getType() == null) {
            throw new IllegalStateException("Workflow 节点缺少 type: " + nodeConfig.getId()
                    + "，" + context);
        }
        if (nodeConfig.getType() == WorkflowNodeType.TOOL) {
            throw new IllegalStateException("TOOL 节点第一版暂未支持: " + nodeConfig.getId()
                    + "，" + context);
        }
        if (nodeConfig.getType() == WorkflowNodeType.AGENT && isBlank(nodeConfig.getAgentId())) {
            throw new IllegalStateException("AGENT 节点缺少 agent-id: " + nodeConfig.getId()
                    + "，" + context);
        }
        if (nodeConfig.getType() != WorkflowNodeType.AGENT && isBlank(nodeConfig.getComponent())) {
            throw new IllegalStateException(nodeConfig.getType() + " 节点缺少 component: "
                    + nodeConfig.getId() + "，" + context);
        }
        return WorkflowNode.builder()
                .nodeId(nodeConfig.getId())
                .name(nodeConfig.getName() != null ? nodeConfig.getName() : nodeConfig.getId())
                .type(nodeConfig.getType())
                .agentId(nodeConfig.getAgentId())
                .component(nodeConfig.getComponent())
                .inputTemplate(nodeConfig.getInputTemplate())
                .outputKey(nodeConfig.getOutputKey())
                .outgoingEdges(new ArrayList<>())
                .build();
    }

    private List<WorkflowEdge> compileEdges(WorkflowConfigModel config,
                                            Map<String, WorkflowNode> nodes, String context) {
        List<WorkflowEdge> edges = new ArrayList<>();
        if (config.getEdges() == null) {
            return edges;
        }
        for (WorkflowConfigModel.EdgeConfig edgeConfig : config.getEdges()) {
            if (edgeConfig == null || isBlank(edgeConfig.getFrom()) || isBlank(edgeConfig.getTo())) {
                throw new IllegalStateException("Workflow 边缺少 from/to: " + context);
            }
            if (!nodes.containsKey(edgeConfig.getFrom()) || !nodes.containsKey(edgeConfig.getTo())) {
                throw new IllegalStateException("Workflow 边引用了不存在的节点: "
                        + edgeConfig.getFrom() + " -> " + edgeConfig.getTo() + "，" + context);
            }
            if (edgeConfig.getFrom().equals(edgeConfig.getTo())) {
                throw new IllegalStateException("Workflow 不允许自环边: " + edgeConfig.getFrom()
                        + "，" + context);
            }
            WorkflowEdge edge = WorkflowEdge.builder()
                    .fromNodeId(edgeConfig.getFrom())
                    .toNodeId(edgeConfig.getTo())
                    .when(edgeConfig.getWhen())
                    .build();
            edges.add(edge);
            nodes.get(edgeConfig.getFrom()).getOutgoingEdges().add(edge);
        }
        return edges;
    }

    private void validateOutgoingEdgeRules(Map<String, WorkflowNode> nodes, String context) {
        for (WorkflowNode node : nodes.values()) {
            List<WorkflowEdge> outgoing = node.getOutgoingEdges();
            boolean isCondition = node.getType() == WorkflowNodeType.CONDITION;
            if (isCondition) {
                validateConditionEdges(node, outgoing, context);
            } else {
                if (outgoing == null || outgoing.isEmpty()) {
                    continue;
                }
                // 非 CONDITION 节点：出边必须全部无条件（可多条 = 并行扇出）
                for (WorkflowEdge edge : outgoing) {
                    if (edge.isConditional()) {
                        throw new IllegalStateException("非 CONDITION 节点出边不允许携带 when: "
                                + node.getNodeId() + "，" + context);
                    }
                }
                // 多条无条件出边即并行扇出，合法，不再限制 ≤1
            }
        }
    }

    /**
     * 汇聚（Join）校验：统计每个节点的无条件入边数量。
     * <ul>
     *   <li>无条件入边 ≥ 2 → 该节点为 Join 屏障，运行时需等待所有并行分支到达</li>
     *   <li>Join 节点禁止同时接收条件入边（并行与条件分支语义冲突）</li>
     * </ul>
     */
    private void validateJoinRules(Map<String, WorkflowNode> nodes, String context) {
        Map<String, Integer> uncondIn = new LinkedHashMap<>();
        Map<String, Integer> condIn = new LinkedHashMap<>();
        for (WorkflowNode node : nodes.values()) {
            if (node.getOutgoingEdges() == null) {
                continue;
            }
            for (WorkflowEdge edge : node.getOutgoingEdges()) {
                if (edge.isConditional()) {
                    condIn.merge(edge.getToNodeId(), 1, Integer::sum);
                } else {
                    uncondIn.merge(edge.getToNodeId(), 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> entry : uncondIn.entrySet()) {
            if (entry.getValue() >= 2) {
                String nodeId = entry.getKey();
                if (condIn.getOrDefault(nodeId, 0) > 0) {
                    throw new IllegalStateException("汇聚节点不可同时接收并行分支与条件分支: "
                            + nodeId + "，" + context);
                }
            }
        }
    }

    private void validateConditionEdges(WorkflowNode node, List<WorkflowEdge> outgoing,
                                        String context) {
        if (outgoing.isEmpty()) {
            throw new IllegalStateException("CONDITION 节点必须有出边: "
                    + node.getNodeId() + "，" + context);
        }
        Set<String> whenKeys = new HashSet<>();
        for (WorkflowEdge edge : outgoing) {
            if (!edge.isConditional()) {
                throw new IllegalStateException("CONDITION 节点出边必须携带 when: "
                        + node.getNodeId() + " -> " + edge.getToNodeId() + "，" + context);
            }
            if (!whenKeys.add(edge.getWhen())) {
                throw new IllegalStateException("CONDITION 节点出边 when 值重复: "
                        + node.getNodeId() + " when=" + edge.getWhen() + "，" + context);
            }
        }
    }

    private void validateAcyclic(Map<String, WorkflowNode> nodes, String context) {
        Map<String, Integer> indegree = new HashMap<>();
        nodes.keySet().forEach(nodeId -> indegree.put(nodeId, 0));
        for (WorkflowNode node : nodes.values()) {
            for (WorkflowEdge edge : node.getOutgoingEdges()) {
                indegree.merge(edge.getToNodeId(), 1, Integer::sum);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .forEach(entry -> queue.add(entry.getKey()));
        int processed = 0;
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            processed++;
            for (WorkflowEdge edge : nodes.get(nodeId).getOutgoingEdges()) {
                int remaining = indegree.merge(edge.getToNodeId(), -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(edge.getToNodeId());
                }
            }
        }
        if (processed != nodes.size()) {
            List<String> inCycle = nodes.keySet().stream()
                    .filter(nodeId -> indegree.get(nodeId) > 0)
                    .toList();
            throw new IllegalStateException("Workflow 存在环（受控 DAG 不允许）: "
                    + inCycle + "，" + context);
        }
    }

    private void validateReachable(String entry, Map<String, WorkflowNode> nodes, String context) {
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(entry);
        while (!stack.isEmpty()) {
            String nodeId = stack.pop();
            if (!visited.add(nodeId)) {
                continue;
            }
            for (WorkflowEdge edge : nodes.get(nodeId).getOutgoingEdges()) {
                stack.push(edge.getToNodeId());
            }
        }
        if (visited.size() != nodes.size()) {
            List<String> unreachable = nodes.keySet().stream()
                    .filter(nodeId -> !visited.contains(nodeId))
                    .toList();
            throw new IllegalStateException("Workflow 存在从 entry 不可达的节点: "
                    + unreachable + "，" + context);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
