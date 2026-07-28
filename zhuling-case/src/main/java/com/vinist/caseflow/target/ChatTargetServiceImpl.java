package com.vinist.caseflow.target;

import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.ChatTarget;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import com.vinist.domain.agent.service.IChatTargetService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatTargetServiceImpl implements IChatTargetService {

    private final IAgentRuntimeRegistry agentRuntimeRegistry;

    public ChatTargetServiceImpl(IAgentRuntimeRegistry agentRuntimeRegistry) {
        this.agentRuntimeRegistry = agentRuntimeRegistry;
    }

    @Override
    public List<ChatTarget> listTargets() {
        return agentRuntimeRegistry.listTargets();
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
        throw new IllegalArgumentException("暂不支持的对话目标类型: " + type);
    }

}
