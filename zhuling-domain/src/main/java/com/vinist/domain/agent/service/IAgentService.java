package com.vinist.domain.agent.service;

import com.vinist.domain.agent.adapter.port.IChatModelPort;
import com.vinist.domain.agent.adapter.port.IToolRegistryPort;

/**
 * Agent 服务接口
 * 
 * <p>领域服务，负责 Agent 的生命周期管理
 */
public interface IAgentService {

    /**
     * 创建 Agent 实例
     * 
     * @param agentId Agent ID
     * @param chatModelPort LLM 调用端口
     * @param toolRegistryPort 工具注册中心
     * @return Agent 实例
     */
    Object createAgent(String agentId, IChatModelPort chatModelPort, IToolRegistryPort toolRegistryPort);

    /**
     * 执行 Agent 对话
     * 
     * @param agentId Agent ID
     * @param message 用户消息
     * @return 对话结果
     */
    String executeChat(String agentId, String message);

    /**
     * 执行流式对话
     * 
     * @param agentId Agent ID
     * @param message 用户消息
     * @return Flux 流式响应
     */
    Object executeStreamChat(String agentId, String message);

}
