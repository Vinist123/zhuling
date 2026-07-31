package com.vinist.domain.agent.model.react;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactExecutionPolicy {

    public static final int DEFAULT_MAX_STEPS = 10;
    public static final int DEFAULT_MAX_TOOL_CALLS = 20;
    public static final long DEFAULT_LLM_TIMEOUT_MS = 120_000L;

    @Builder.Default
    private int maxSteps = DEFAULT_MAX_STEPS;

    @Builder.Default
    private int maxToolCalls = DEFAULT_MAX_TOOL_CALLS;

    @Builder.Default
    private long llmTimeoutMs = DEFAULT_LLM_TIMEOUT_MS;

}
