package com.vinist.infrastructure.gateway.mcp;

import com.vinist.domain.agent.model.ModuleConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

/**
 * SSE MCP ToolCallback 构建器
 */
@Slf4j
@Component
public class SseMcpToolCallbackBuilder implements McpToolCallbackBuilder {

    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 30_000L;
    private static final String DEFAULT_SSE_ENDPOINT = "/sse";
    private static final int MAX_INIT_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2_000L;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    @Override
    public boolean supports(ModuleConfig.McpServerConfig serverConfig) {
        return serverConfig != null && "sse".equalsIgnoreCase(serverConfig.getType());
    }

    @Override
    public ToolCallback[] build(ModuleConfig.McpServerConfig serverConfig) {
        String originalUrl = requireText(serverConfig.getUrl(), "SSE MCP url 不能为空");
        String baseUrl = originalUrl;
        String sseEndpoint = serverConfig.getSseEndpoint();

        if (sseEndpoint == null || sseEndpoint.isBlank()) {
            SseAddress sseAddress = splitSseAddress(originalUrl);
            baseUrl = sseAddress.baseUrl();
            sseEndpoint = sseAddress.sseEndpoint();
        }

        String resolvedSseEndpoint = (sseEndpoint == null || sseEndpoint.isBlank()) ? DEFAULT_SSE_ENDPOINT : sseEndpoint;

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(resolvedSseEndpoint)
                .build();

        long timeoutMs = resolveRequestTimeoutMs(serverConfig);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofMillis(timeoutMs))
                .build();

        initializeWithRetry(client, serverConfig.getName(), baseUrl, resolvedSseEndpoint, timeoutMs);

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .build()
                .getToolCallbacks();
    }

    private void initializeWithRetry(McpSyncClient client, String serverName,
                                     String baseUrl, String sseEndpoint, long timeoutMs) {
        for (int attempt = 1; attempt <= MAX_INIT_RETRIES; attempt++) {
            try {
                log.info("初始化 SSE MCP 客户端 [尝试 {}/{}]: serverName={}, baseUrl={}, sseEndpoint={}, timeoutMs={}",
                        attempt, MAX_INIT_RETRIES, serverName, baseUrl, sseEndpoint, timeoutMs);
                McpSchema.InitializeResult result = client.initialize();
                log.info("初始化 SSE MCP 客户端成功: serverName={}, initialize={}", serverName, result);
                return;
            } catch (Exception e) {
                log.warn("初始化 SSE MCP 客户端失败 [尝试 {}/{}]: serverName={}, error={}",
                        attempt, MAX_INIT_RETRIES, serverName, e.getMessage());
                if (attempt >= MAX_INIT_RETRIES) {
                    throw new RuntimeException("SSE MCP 客户端初始化失败，已重试 " + MAX_INIT_RETRIES + " 次: serverName=" + serverName, e);
                }
                long delayMs = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
                log.info("等待 {}ms 后进行第 {} 次重试", delayMs, attempt + 1);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("SSE MCP 客户端初始化重试被中断: serverName=" + serverName, ie);
                }
            }
        }
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

    private SseAddress splitSseAddress(String originalUrl) {
        try {
            URL url = URI.create(originalUrl).toURL();
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String baseUrl = port == -1 ? protocol + "://" + host : protocol + "://" + host + ":" + port;
            String path = url.getPath();
            return new SseAddress(baseUrl, (path == null || path.isBlank()) ? DEFAULT_SSE_ENDPOINT : path);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("非法 SSE MCP url: " + originalUrl, e);
        }
    }

    private record SseAddress(String baseUrl, String sseEndpoint) {
    }

}
