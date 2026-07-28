package com.vinist.infrastructure.adapter.port;

import com.vinist.domain.agent.adapter.port.IToolRegistryPort;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import com.vinist.infrastructure.gateway.mcp.McpToolCallbackFactory;
import com.vinist.infrastructure.gateway.skills.SkillToolCallbackFactory;
import com.vinist.infrastructure.observability.TelemetryToolCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册端口实现
 * 
 * <p>统一管理三种工具来源：
 * <ul>
 *   <li>本地工具：通过 @Tool 注解的 Bean，由 MethodToolCallbackProvider 注册</li>
 *   <li>MCP Server：通过 SSE/Stdio 连接的远程 MCP Server</li>
 *   <li>Skills：通过 YAML/Resource 文件定义的工具集</li>
 * </ul>
 * 
 * <p>参考旧脚手架：
 * - LocalToolMcpCreateService：从 ApplicationContext 获取 ToolCallbackProvider Bean
 * - DefaultArmoryFactory.DynamicContext：收集所有 ToolCallback
 * - ChatModelNode：将工具回调注入到 OpenAiChatModel
 */
@Slf4j
public class ToolRegistryPortImpl implements IToolRegistryPort {

    private final String agentId;
    private final ApplicationContext applicationContext;
    private final McpToolCallbackFactory mcpToolCallbackFactory;
    private final SkillToolCallbackFactory skillToolCallbackFactory;
    private final ITurnTelemetryService turnTelemetryService;
    private final IConversationStreamEventPublisher eventPublisher;
    private final Map<String, ToolCallback> registeredTools = new LinkedHashMap<>();

    public ToolRegistryPortImpl(String agentId,
                                ApplicationContext applicationContext,
                                McpToolCallbackFactory mcpToolCallbackFactory,
                                SkillToolCallbackFactory skillToolCallbackFactory,
                                ITurnTelemetryService turnTelemetryService,
                                IConversationStreamEventPublisher eventPublisher) {
        this.agentId = agentId;
        this.applicationContext = applicationContext;
        this.mcpToolCallbackFactory = mcpToolCallbackFactory;
        this.skillToolCallbackFactory = skillToolCallbackFactory;
        this.turnTelemetryService = turnTelemetryService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void registerLocalTool(String toolName) {
        try {
            Object bean = applicationContext.getBean(toolName);
            ToolCallbackProvider provider;

            if (bean instanceof ToolCallbackProvider toolCallbackProvider) {
                provider = toolCallbackProvider;
            } else {
                provider = MethodToolCallbackProvider.builder()
                        .toolObjects(bean)
                        .build();
            }

            ToolCallback[] callbacks = provider.getToolCallbacks();
            for (ToolCallback callback : callbacks) {
                ToolCallback wrappedCallback = wrapToolCallback(callback, "local", toolName);
                String callbackName = wrappedCallback.getToolDefinition().name();
                registeredTools.put(callbackName, wrappedCallback);
                log.info("注册本地工具: beanName={}, toolName={}", toolName, callbackName);
            }
        } catch (Exception e) {
            log.error("注册本地工具失败: {}", toolName, e);
        }
    }

    @Override
    public void registerMcpTools(ModuleConfig.McpConfig config) {
        if (config == null) {
            log.warn("忽略空 MCP 配置");
            return;
        }

        if (!Boolean.TRUE.equals(config.getEnabled()) || config.getServers() == null || config.getServers().isEmpty()) {
            log.info("MCP 未启用或无服务器配置");
            return;
        }

        RuntimeException registrationFailure = null;
        for (ModuleConfig.McpServerConfig serverConfig : config.getServers()) {
            if (serverConfig == null || serverConfig.getType() == null || serverConfig.getType().isBlank()) {
                continue;
            }

            try {
                ToolCallback[] callbacks = mcpToolCallbackFactory.build(serverConfig);
                for (ToolCallback callback : callbacks) {
                    ToolCallback wrappedCallback = wrapToolCallback(callback, serverConfig.getType(), serverConfig.getName());
                    String callbackName = wrappedCallback.getToolDefinition().name();
                    registeredTools.put(callbackName, wrappedCallback);
                    log.info("注册 MCP 工具: serverName={}, serverType={}, toolName={}",
                            serverConfig.getName(), serverConfig.getType(), callbackName);
                }
            } catch (Exception e) {
                log.error("注册 MCP 工具失败: serverName={}, serverType={}",
                        serverConfig.getName(), serverConfig.getType(), e);
                if (registrationFailure == null) {
                    registrationFailure = new IllegalStateException(
                            "MCP 工具注册失败: " + serverConfig.getName(), e);
                }
            }
        }
        if (registrationFailure != null) {
            throw registrationFailure;
        }
    }

    @Override
    public void registerSkillsTools(List<ModuleConfig.SkillConfig> skillsConfig) {
        if (skillsConfig == null || skillsConfig.stream()
                .noneMatch(skill -> skill != null && Boolean.TRUE.equals(skill.getEnabled()))) {
            log.info("Skills 未启用或无配置: agentId={}", agentId);
            return;
        }

        try {
            ToolCallback[] callbacks = skillToolCallbackFactory.build(agentId, skillsConfig);
            for (ToolCallback callback : callbacks) {
                ToolCallback wrappedCallback = wrapToolCallback(callback, "skill", agentId);
                String callbackName = wrappedCallback.getToolDefinition().name();
                registeredTools.put(callbackName, wrappedCallback);
                log.info("注册 Skill 工具: agentId={}, toolName={}", agentId, callbackName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Skill 工具注册失败: agentId=" + agentId, e);
        }
    }

    @Override
    public ToolCallback[] getAllToolCallbacks() {
        return registeredTools.values().toArray(new ToolCallback[0]);
    }

    @Override
    public ToolCallback getToolCallback(String toolName) {
        return registeredTools.get(toolName);
    }

    @Override
    public List<String> getRegisteredToolNames() {
        return List.copyOf(registeredTools.keySet());
    }

    private ToolCallback wrapToolCallback(ToolCallback callback, String toolType, String serverName) {
        return new TelemetryToolCallback(callback, toolType, serverName, turnTelemetryService, eventPublisher);
    }

}
