package com.vinist.domain.agent.model;

import com.vinist.domain.agent.adapter.port.IChatModelPort;
import com.vinist.domain.agent.adapter.port.IToolRegistryPort;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRuntime {

    private String agentId;

    private AgentConfigModel config;

    private IChatModelPort chatModelPort;

    private IToolRegistryPort toolRegistryPort;

}
