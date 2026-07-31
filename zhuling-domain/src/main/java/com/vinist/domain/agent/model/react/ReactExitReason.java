package com.vinist.domain.agent.model.react;

public enum ReactExitReason {
    COMPLETED,
    TOOL_ACTION_REQUIRED,
    MAX_STEPS_REACHED,
    MAX_TOOL_CALLS_REACHED,
    LLM_TIMEOUT,
    MODEL_ERROR
}
