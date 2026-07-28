package com.vinist.infrastructure.gateway.mcp;

import com.vinist.domain.agent.model.ModuleConfig;
import org.springframework.ai.tool.ToolCallback;

/**
 * MCP ToolCallback 构建器
 */
public interface McpToolCallbackBuilder {

    /**
     * 是否支持当前 MCP server 配置
     */
    boolean supports(ModuleConfig.McpServerConfig serverConfig);

    /**
     * 构建工具回调
     */
    ToolCallback[] build(ModuleConfig.McpServerConfig serverConfig);

}
