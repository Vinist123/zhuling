package com.vinist.infrastructure.runtime.react;

import com.vinist.domain.agent.adapter.port.IReactModelPort;
import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.infrastructure.gateway.IChatModelGatewayService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

/**
 * 创建使用 Spring AI 原生 tool_calls 的 ReAct 模型端口。
 */
@Service
public class NativeReactModelPortFactory {

    private final IChatModelGatewayService chatModelGatewayService;

    public NativeReactModelPortFactory(IChatModelGatewayService chatModelGatewayService) {
        this.chatModelGatewayService = chatModelGatewayService;
    }

    public IReactModelPort create(AgentConfigModel config, ToolCallback[] toolCallbacks) {
        ChatModel chatModel = chatModelGatewayService.createChatModel(
                config, toolCallbacks != null ? toolCallbacks : new ToolCallback[0]);
        return new SpringAiNativeReactModelPort(chatModel);
    }

}
