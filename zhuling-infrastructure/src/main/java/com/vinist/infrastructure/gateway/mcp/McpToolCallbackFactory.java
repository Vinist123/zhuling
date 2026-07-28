package com.vinist.infrastructure.gateway.mcp;

import com.vinist.domain.agent.model.ModuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP ToolCallback 工厂
 */
@Slf4j
@Service
public class McpToolCallbackFactory {

    private final List<McpToolCallbackBuilder> builders;

    public McpToolCallbackFactory(List<McpToolCallbackBuilder> builders) {
        this.builders = builders;
    }

    public ToolCallback[] build(ModuleConfig.McpServerConfig serverConfig) {
        return builders.stream()
                .filter(builder -> builder.supports(serverConfig))
                .findFirst()
                .map(builder -> builder.build(serverConfig))
                .orElseThrow(() -> new IllegalArgumentException(
                        "未找到支持的 MCP server 类型: " + (serverConfig != null ? serverConfig.getType() : null)));
    }

}
