package com.vinist.infrastructure.gateway.impl;

import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.infrastructure.gateway.IChatModelGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 聊天模型网关服务实现
 * 
 * <p>通过 OpenAI 兼容接口创建 ChatClient/ChatModel
 * 支持所有 OpenAI 兼容的 LLM 服务（通义千问、智谱、Moonshot、中转站等）
 * 
 * <p>Spring AI 2.0-M5 使用 OpenAiChatModel.builder() + OpenAiChatOptions 构建
 */
@Slf4j
@Service
public class ChatModelGatewayServiceImpl implements IChatModelGatewayService {

    @Override
    public ChatClient createChatClient(AgentConfigModel config) {
        return createChatClient(config, new ToolCallback[0]);
    }

    @Override
    public ChatClient createChatClient(AgentConfigModel config, ToolCallback[] toolCallbacks) {
        ChatModel chatModel = createChatModel(config, toolCallbacks);
        return ChatClient.builder(chatModel).build();
    }

    @Override
    public ChatModel createChatModel(AgentConfigModel config) {
        return createChatModel(config, new ToolCallback[0]);
    }

    @Override
    public ChatModel createChatModel(AgentConfigModel config, ToolCallback[] toolCallbacks) {
        ModuleConfig moduleConfig = config.getModule();
        ModuleConfig.AiApiConfig aiApiConfig = moduleConfig.getAiApi();
        ModuleConfig.ChatModelConfig chatModelConfig = moduleConfig.getChatModel();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .baseUrl(aiApiConfig.getBaseUrl())
                .apiKey(aiApiConfig.getApiKey())
                .model(chatModelConfig != null ? chatModelConfig.getModel() : null)
                .toolCallbacks(Arrays.asList(toolCallbacks));

        // 透传 extraBody（如 chat_template_kwargs 等模型专有参数）
        if (chatModelConfig != null && chatModelConfig.getExtraBody() != null && !chatModelConfig.getExtraBody().isEmpty()) {
            optionsBuilder.extraBody(chatModelConfig.getExtraBody());
        }

        OpenAiChatOptions options = optionsBuilder.build();

        log.info("创建 ChatModel: agentId={}, baseUrl={}, model={}, toolCount={}",
                config.getId(),
                aiApiConfig.getBaseUrl(),
                chatModelConfig != null ? chatModelConfig.getModel() : null,
                toolCallbacks.length);
        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

}
