package com.vinist.infrastructure.gateway.mcp;

import com.vinist.domain.agent.model.ModuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地 MCP ToolCallback 构建器
 */
@Slf4j
@Component
public class LocalMcpToolCallbackBuilder implements McpToolCallbackBuilder {

    private final ApplicationContext applicationContext;

    public LocalMcpToolCallbackBuilder(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean supports(ModuleConfig.McpServerConfig serverConfig) {
        return serverConfig != null && "local".equalsIgnoreCase(serverConfig.getType());
    }

    @Override
    public ToolCallback[] build(ModuleConfig.McpServerConfig serverConfig) {
        List<ToolCallback> callbacks = new ArrayList<>();
        if (serverConfig.getTools() == null) {
            return new ToolCallback[0];
        }

        for (ModuleConfig.LocalToolConfig toolConfig : serverConfig.getTools()) {
            if (toolConfig == null || !Boolean.TRUE.equals(toolConfig.getEnabled())) {
                continue;
            }

            Object bean = applicationContext.getBean(toolConfig.getName());
            ToolCallbackProvider provider = bean instanceof ToolCallbackProvider toolCallbackProvider
                    ? toolCallbackProvider
                    : MethodToolCallbackProvider.builder().toolObjects(bean).build();

            ToolCallback[] localCallbacks = provider.getToolCallbacks();
            for (ToolCallback callback : localCallbacks) {
                callbacks.add(callback);
                log.info("构建本地 MCP 工具: serverName={}, beanName={}, toolName={}",
                        serverConfig.getName(), toolConfig.getName(), callback.getToolDefinition().name());
            }
        }

        return callbacks.toArray(new ToolCallback[0]);
    }

}
