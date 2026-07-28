package com.vinist.infrastructure.gateway.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinist.domain.agent.model.ModuleConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Stdio MCP ToolCallback 构建器
 */
@Slf4j
@Component
public class StdioMcpToolCallbackBuilder implements McpToolCallbackBuilder {

    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 30_000L;

    @Override
    public boolean supports(ModuleConfig.McpServerConfig serverConfig) {
        return serverConfig != null && "stdio".equalsIgnoreCase(serverConfig.getType());
    }

    @Override
    public ToolCallback[] build(ModuleConfig.McpServerConfig serverConfig) {
        String command = requireText(serverConfig.getCommand(), "Stdio MCP command 不能为空");
        List<String> args = serverConfig.getArgs() != null ? serverConfig.getArgs() : List.of();
        Map<String, String> env = serverConfig.getEnv() != null ? serverConfig.getEnv() : Map.of();

        ServerParameters stdioParams = ServerParameters.builder(command)
                .args(args)
                .env(env)
                .build();

        McpSyncClient client = McpClient.sync(
                        new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper())))
                .requestTimeout(Duration.ofMillis(resolveRequestTimeoutMs(serverConfig)))
                .build();

        McpSchema.InitializeResult initialize = client.initialize();
        log.info("初始化 Stdio MCP 客户端成功: serverName={}, command={}, initialize={}",
                serverConfig.getName(), command, initialize);

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .build()
                .getToolCallbacks();
    }

    private long resolveRequestTimeoutMs(ModuleConfig.McpServerConfig serverConfig) {
        return serverConfig.getRequestTimeoutMs() != null ? serverConfig.getRequestTimeoutMs() : DEFAULT_REQUEST_TIMEOUT_MS;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

}
