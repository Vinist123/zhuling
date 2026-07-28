package com.vinist.infrastructure.runtime;

import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import com.vinist.infrastructure.adapter.port.ChatModelPortImpl;
import com.vinist.infrastructure.adapter.port.ToolRegistryPortImpl;
import com.vinist.infrastructure.gateway.IChatModelGatewayService;
import com.vinist.infrastructure.gateway.mcp.McpToolCallbackFactory;
import com.vinist.infrastructure.gateway.skills.SkillToolCallbackFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeFactory {

    private final ApplicationContext applicationContext;
    private final McpToolCallbackFactory mcpToolCallbackFactory;
    private final SkillToolCallbackFactory skillToolCallbackFactory;
    private final ITurnTelemetryService turnTelemetryService;
    private final IConversationStreamEventPublisher eventPublisher;
    private final IChatModelGatewayService chatModelGatewayService;

    public AgentRuntimeFactory(ApplicationContext applicationContext,
                               McpToolCallbackFactory mcpToolCallbackFactory,
                               SkillToolCallbackFactory skillToolCallbackFactory,
                               ITurnTelemetryService turnTelemetryService,
                               IConversationStreamEventPublisher eventPublisher,
                               IChatModelGatewayService chatModelGatewayService) {
        this.applicationContext = applicationContext;
        this.mcpToolCallbackFactory = mcpToolCallbackFactory;
        this.skillToolCallbackFactory = skillToolCallbackFactory;
        this.turnTelemetryService = turnTelemetryService;
        this.eventPublisher = eventPublisher;
        this.chatModelGatewayService = chatModelGatewayService;
    }

    public AgentRuntime create(AgentConfigModel config) {
        ToolRegistryPortImpl toolRegistry = new ToolRegistryPortImpl(
                config.getId(), applicationContext, mcpToolCallbackFactory,
                skillToolCallbackFactory, turnTelemetryService, eventPublisher);
        registerConfiguredTools(config, toolRegistry);

        ChatClient chatClient = chatModelGatewayService.createChatClient(
                config, toolRegistry.getAllToolCallbacks());
        return AgentRuntime.builder()
                .agentId(config.getId())
                .config(config)
                .chatModelPort(new ChatModelPortImpl(chatClient, turnTelemetryService, eventPublisher))
                .toolRegistryPort(toolRegistry)
                .build();
    }

    private void registerConfiguredTools(AgentConfigModel config, ToolRegistryPortImpl toolRegistry) {
        ModuleConfig moduleConfig = config.getModule();
        if (moduleConfig == null) {
            return;
        }
        if (moduleConfig.getMcp() != null) {
            toolRegistry.registerMcpTools(moduleConfig.getMcp());
        }
        toolRegistry.registerSkillsTools(moduleConfig.getSkills());
    }

}
