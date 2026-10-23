package com.vinist.caseflow.target;

import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.ChatTarget;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.model.workflow.WorkflowRuntime;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import com.vinist.domain.agent.service.IChatTargetService;
import com.vinist.domain.agent.service.IWorkflowConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatTargetServiceImpl implements IChatTargetService {

    private static final String AVAILABLE = "AVAILABLE";

    private final IAgentRuntimeRegistry agentRuntimeRegistry;
    private final IWorkflowConfigService workflowConfigService;

    public ChatTargetServiceImpl(IAgentRuntimeRegistry agentRuntimeRegistry,
                                 IWorkflowConfigService workflowConfigService) {
        this.agentRuntimeRegistry = agentRuntimeRegistry;
        this.workflowConfigService = workflowConfigService;
    }

    @Override
    public List<ChatTarget> listTargets() {
        List<ChatTarget> targets = new ArrayList<>(agentRuntimeRegistry.listTargets());
        for (WorkflowRuntime workflowRuntime : workflowConfigService.getAllWorkflowRuntimes()) {
            targets.add(toWorkflowTarget(workflowRuntime));
        }
        return targets;
    }

    @Override
    public ChatTarget getRequired(ChatTargetType type, String id) {
        if (type == null || type == ChatTargetType.AGENT) {
            AgentRuntime runtime = agentRuntimeRegistry.getRequired(id);
            return agentRuntimeRegistry.listTargets().stream()
                    .filter(target -> target.getType() == ChatTargetType.AGENT && target.getId().equals(runtime.getAgentId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("对话目标不存在: " + id));
        }
        if (type == ChatTargetType.WORKFLOW) {
            return toWorkflowTarget(workflowConfigService.getRequired(id));
        }
        throw new IllegalArgumentException("暂不支持的对话目标类型: " + type);
    }

    private ChatTarget toWorkflowTarget(WorkflowRuntime workflowRuntime) {
        return ChatTarget.builder()
                .id(workflowRuntime.getDefinition().getId())
                .type(ChatTargetType.WORKFLOW)
                .name(workflowRuntime.getDefinition().getName())
                .description(workflowRuntime.getDefinition().getDescription())
                .status(AVAILABLE)
                .build();
    }

}
