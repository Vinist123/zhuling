package com.vinist.domain.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 配置模型
 * 
 * <p>对应 agent-config/agents/*.yml 的单个文件根节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigModel {

    /** Agent ID */
    private String id;

    /** 应用名称 */
    private String appName;

    /** Agent 元信息 */
    private AgentInfo agent;

    /** 模块配置 */
    private ModuleConfig module;

    /**
     * Agent 元信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentInfo {
        /** Agent ID */
        private String agentId;
        /** Agent 名称 */
        private String agentName;
        /** Agent 描述 */
        private String agentDesc;
    }

}
