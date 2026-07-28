package com.vinist.infrastructure.gateway;

import com.vinist.domain.agent.model.AgentConfigModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * 聊天模型网关服务
 * 
 * <p>通过 OpenAI 兼容接口创建 ChatClient
 */
public interface IChatModelGatewayService {

    /**
     * 根据配置创建 ChatClient
     * 
     * @param config Agent 配置
     * @return ChatClient
     */
    ChatClient createChatClient(AgentConfigModel config);

    /**
     * 根据配置和工具回调创建 ChatClient
     *
     * @param config Agent 配置
     * @param toolCallbacks 工具回调
     * @return ChatClient
     */
    ChatClient createChatClient(AgentConfigModel config, ToolCallback[] toolCallbacks);

    /**
     * 根据配置创建 ChatModel
     * 
     * @param config Agent 配置
     * @return ChatModel
     */
    ChatModel createChatModel(AgentConfigModel config);

    /**
     * 根据配置和工具回调创建 ChatModel
     *
     * @param config Agent 配置
     * @param toolCallbacks 工具回调
     * @return ChatModel
     */
    ChatModel createChatModel(AgentConfigModel config, ToolCallback[] toolCallbacks);

}
