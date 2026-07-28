package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.AgentConfigModel;

import java.util.List;

/**
 * Agent 配置服务接口
 * 
 * <p>负责从 agent-config/agents/*.yml 读取 Agent 配置
 */
public interface IAgentConfigService {

    /**
     * 根据 Agent ID 获取配置
     * 
     * @param agentId Agent ID
     * @return Agent 配置
     */
    AgentConfigModel getAgentConfig(String agentId);

    /**
     * 获取所有已注册的 Agent 配置
     * 
     * @return Agent 配置列表
     */
    List<AgentConfigModel> getAllAgentConfigs();

}
