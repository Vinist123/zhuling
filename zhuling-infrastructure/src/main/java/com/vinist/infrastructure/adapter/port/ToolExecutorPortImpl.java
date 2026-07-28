package com.vinist.infrastructure.adapter.port;

import com.vinist.domain.agent.adapter.port.IToolExecutorPort;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
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

}
