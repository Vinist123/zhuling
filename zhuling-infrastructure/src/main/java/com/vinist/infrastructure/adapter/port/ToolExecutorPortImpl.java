package com.vinist.infrastructure.adapter.port;

import com.vinist.domain.agent.adapter.port.IToolExecutorPort;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.react.ToolExecutionResult;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import com.vinist.infrastructure.observability.TelemetryToolCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具执行端口实现
 *
 * <p>负责基于 ToolRegistry 定位具体工具并执行，便于后续接入 ReAct 主循环、
 * 手动工具调试入口或工具可观测性记录。
 */
@Slf4j
@Service
public class ToolExecutorPortImpl implements IToolExecutorPort {

    private final IAgentRuntimeRegistry agentRuntimeRegistry;

    public ToolExecutorPortImpl(IAgentRuntimeRegistry agentRuntimeRegistry) {
        this.agentRuntimeRegistry = agentRuntimeRegistry;
    }

    @Override
    public String execute(String targetId, String toolName, String toolInput) {
        AgentRuntime runtime = agentRuntimeRegistry.getRequired(targetId);
        ToolCallback toolCallback = runtime.getToolRegistryPort().getToolCallback(toolName);
        if (toolCallback == null) {
            throw new IllegalArgumentException("未找到已注册工具: " + toolName);
        }

        if (toolInput == null || toolInput.isBlank()) {
            throw new IllegalArgumentException("工具输入不能为空");
        }

        try {
            // 尝试解析 JSON，提前暴露格式问题
            new ObjectMapper().readTree(toolInput);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "工具输入必须是合法 JSON，当前输入: " + toolInput + "，错误: " + e.getMessage(), e);
        }

        log.info("执行工具: toolName={}, toolInput={}", toolName, toolInput);
        String result = toolCallback.call(toolInput);
        log.info("执行工具完成: toolName={}, resultLength={}", toolName, result != null ? result.length() : 0);
        return result;
    }

    @Override
    public ToolExecutionResult executeWithMetadata(String targetId, String toolName, String toolInput) {
        AgentRuntime runtime = agentRuntimeRegistry.getRequired(targetId);
        ToolCallback toolCallback = runtime.getToolRegistryPort().getToolCallback(toolName);
        if (toolCallback == null) {
            throw new IllegalArgumentException("未找到已注册工具: " + toolName);
        }
        if (toolInput == null || toolInput.isBlank()) {
            throw new IllegalArgumentException("工具输入不能为空");
        }
        try {
            new ObjectMapper().readTree(toolInput);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "工具输入必须是合法 JSON，当前输入: " + toolInput + "，错误: " + e.getMessage(), e);
        }

        // 从 TelemetryToolCallback 提取 serverName/toolType（不依赖 ThreadLocal）
        String serverName = null;
        String toolType = null;
        if (toolCallback instanceof TelemetryToolCallback ttc) {
            serverName = ttc.getServerName();
            toolType = ttc.getToolType();
        }

        log.info("执行工具(含元数据): toolName={}, toolInput={}", toolName, toolInput);
        long start = System.currentTimeMillis();
        try {
            String result = toolCallback.call(toolInput);
            long durationMs = System.currentTimeMillis() - start;
            log.info("执行工具完成: toolName={}, resultLength={}, durationMs={}",
                    toolName, result != null ? result.length() : 0, durationMs);
            return new ToolExecutionResult(result, durationMs, serverName, toolType, true, null);
        } catch (RuntimeException ex) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("执行工具失败: toolName={}, durationMs={}, error={}", toolName, durationMs, ex.getMessage());
            return new ToolExecutionResult(null, durationMs, serverName, toolType, false,
                    ex.getMessage());
        }
    }

}
