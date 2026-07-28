package com.vinist.trigger.http;

import com.vinist.api.dto.ToolExecuteRequestDTO;
import com.vinist.api.response.Response;
import com.vinist.api.response.ToolExecuteResponseVO;
import com.vinist.domain.agent.adapter.port.IToolExecutorPort;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具调试控制器
 *
 * <p>用于验证本地工具的注册、发现与执行链路。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final IAgentRuntimeRegistry agentRuntimeRegistry;
    private final IToolExecutorPort toolExecutorPort;

    public ToolController(IAgentRuntimeRegistry agentRuntimeRegistry, IToolExecutorPort toolExecutorPort) {
        this.agentRuntimeRegistry = agentRuntimeRegistry;
        this.toolExecutorPort = toolExecutorPort;
    }

    @GetMapping
    public Response<List<String>> listTools(@RequestParam String targetId) {
        AgentRuntime runtime = agentRuntimeRegistry.getRequired(targetId);
        List<String> toolNames = runtime.getToolRegistryPort().getRegisteredToolNames();
        log.info("查询已注册工具: toolCount={}, tools={}", toolNames.size(), toolNames);

        return Response.<List<String>>builder()
                .code("00000")
                .info("success")
                .data(toolNames)
                .build();
    }

    @PostMapping("/execute")
    public Response<ToolExecuteResponseVO> executeTool(@RequestBody ToolExecuteRequestDTO request) {
        log.info("收到工具执行请求: targetId={}, toolName={}", request.getTargetId(), request.getToolName());
        String result = toolExecutorPort.execute(request.getTargetId(), request.getToolName(), request.getToolInput());

        return Response.<ToolExecuteResponseVO>builder()
                .code("00000")
                .info("success")
                .data(ToolExecuteResponseVO.builder()
                        .toolName(request.getToolName())
                        .result(result)
                        .build())
                .build();
    }

}
