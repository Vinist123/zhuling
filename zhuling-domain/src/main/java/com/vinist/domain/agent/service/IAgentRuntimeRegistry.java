package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.ChatTarget;

import java.util.List;

public interface IAgentRuntimeRegistry {

    AgentRuntime getRequired(String agentId);

    List<ChatTarget> listTargets();

}
