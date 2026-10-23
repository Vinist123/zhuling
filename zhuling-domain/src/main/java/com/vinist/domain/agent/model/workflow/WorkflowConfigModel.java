package com.vinist.domain.agent.model.workflow;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Workflow YAML 配置映射模型（agent-config/workflows/*.yml）。
 *
 * <p>仅承载 YAML 声明内容；编译校验与组件解析见 WorkflowCompiler。</p>
 */
@Data
public class WorkflowConfigModel {

    /** Workflow id（必填，全局唯一） */
    private String id;

    /** 展示名（必填） */
    private String name;

    /** 描述 */
    private String description;

    /** 入口节点 id（必填） */
    private String entry;

    /** 初始共享变量（黑板 variables 初始值） */
    private Map<String, Object> state;

    /** 节点列表 */
    private List<NodeConfig> nodes;

    /** 边列表 */
    private List<EdgeConfig> edges;

    @Data
    public static class NodeConfig {

        /** 节点 id（Workflow 内唯一） */
        private String id;

        /** 节点展示名 */
        private String name;

        /** 节点类型：AGENT / CODE / TOOL / CONDITION */
        private WorkflowNodeType type;

        /** AGENT 节点：引用的已注册 Agent id */
        private String agentId;

        /** CODE/TOOL/CONDITION 节点：显式注册的 Java 组件名 */
        private String component;

        /** AGENT 节点：输入模板，支持 ${key} 占位符 */
        private String inputTemplate;

        /** 输出写入黑板的 key */
        private String outputKey;

    }

    @Data
    public static class EdgeConfig {

        /** 起点节点 id */
        private String from;

        /** 终点节点 id */
        private String to;

        /** 条件键（仅 CONDITION 节点出边允许） */
        private String when;

    }

}
