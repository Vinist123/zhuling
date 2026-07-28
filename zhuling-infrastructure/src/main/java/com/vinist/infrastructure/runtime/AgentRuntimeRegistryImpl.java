package com.vinist.infrastructure.runtime;

import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.ChatTarget;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.service.IAgentConfigService;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRuntimeRegistryImpl implements IAgentRuntimeRegistry {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final Map<String, AgentRuntime> runtimes = new LinkedHashMap<>();
    private final Map<String, ChatTarget> targets = new LinkedHashMap<>();

    public AgentRuntimeRegistryImpl(IAgentConfigService configService,
                                    AgentRuntimeFactory runtimeFactory) {
        List<AgentConfigModel> configs = configService.getAllAgentConfigs();
        if (configs.isEmpty()) {
            throw new IllegalStateException("未找到可用的 Agent 配置");
        }

        for (AgentConfigModel config : configs) {
            register(config, runtimeFactory);
        }
    }

    @Override
    public synchronized AgentRuntime getRequired(String agentId) {
        AgentRuntime runtime = runtimes.get(agentId);
        if (runtime != null) {
            return runtime;
        }

        ChatTarget target = targets.get(agentId);
        if (target != null && UNAVAILABLE.equals(target.getStatus())) {
            throw new IllegalStateException("Agent runtime unavailable: " + agentId
                    + ", reason=" + target.getUnavailableReason());
        }
        throw new IllegalArgumentException("Agent runtime not found: " + agentId);
    }

    @Override
    public synchronized List<ChatTarget> listTargets() {
        return List.copyOf(targets.values());
    }

    private void register(AgentConfigModel config, AgentRuntimeFactory runtimeFactory) {
        String agentId = config.getId();
        try {
            AgentRuntime runtime = runtimeFactory.create(config);
            runtimes.put(agentId, runtime);
            targets.put(agentId, buildTarget(config, AVAILABLE, null));
        } catch (RuntimeException ex) {
            targets.put(agentId, buildTarget(config, UNAVAILABLE, safeMessage(ex)));
        }
    }

    private ChatTarget buildTarget(AgentConfigModel config, String status, String reason) {
        AgentConfigModel.AgentInfo agentInfo = config.getAgent();
        return ChatTarget.builder()
                .id(config.getId())
                .type(ChatTargetType.AGENT)
                .name(agentInfo != null && agentInfo.getAgentName() != null
                        ? agentInfo.getAgentName() : config.getId())
                .description(agentInfo != null ? agentInfo.getAgentDesc() : null)
                .status(status)
                .unavailableReason(reason)
                .build();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

}
