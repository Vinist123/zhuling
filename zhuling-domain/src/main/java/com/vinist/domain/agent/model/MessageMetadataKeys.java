package com.vinist.domain.agent.model;

/**
 * 消息 metadata 字段常量
 *
 * <p>Phase 5 约定 metadata 使用固定顶层结构，避免后续不同阶段随意扩展造成
 * 查询和持久化语义不稳定。
 */
public final class MessageMetadataKeys {

    private MessageMetadataKeys() {
    }

    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String RECORD_TYPE = "recordType";
    public static final String MESSAGE_ROLE = "messageRole";
    public static final String SESSION_ID = "sessionId";
    public static final String TARGET_ID = "targetId";
    public static final String TARGET_TYPE = "targetType";
    public static final String AGENT_ID = "agentId";
    public static final String STREAM = "stream";
    public static final String STATUS = "status";
    public static final String MODEL = "model";
    public static final String OBSERVABILITY = "observability";
    public static final String TRACE = "trace";
    public static final String CONTEXT = "context";
    public static final String REASONING = "reasoning";
    public static final String TOOL_CALLS = "toolCalls";
    public static final String REACT = "react";
    public static final String TIMING = "timing";
    public static final String PAYLOAD = "payload";
    public static final String USAGE = "usage";
    public static final String ERROR = "error";
    public static final String EXT = "ext";

    public static final String MODEL_NAME = "name";
    public static final String MODEL_BASE_URL = "baseUrl";

    public static final String OBS_REACT_ENABLED = "reactEnabled";
    public static final String OBS_REASONING_CONTENT_ENABLED = "reasoningContentEnabled";
    public static final String OBS_TOOL_CALL_ENABLED = "toolCallEnabled";

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_REQUEST_ID = "requestId";
    public static final String TRACE_TURN_ID = "turnId";

    public static final String CONTEXT_AVAILABLE_MESSAGE_COUNT = "availableMessageCount";
    public static final String CONTEXT_INCLUDED_MESSAGE_COUNT = "includedMessageCount";
    public static final String CONTEXT_PROMPT_CHARACTER_COUNT = "promptCharacterCount";
    public static final String CONTEXT_ESTIMATED_PROMPT_TOKENS = "estimatedPromptTokens";
    public static final String CONTEXT_WINDOW_TOKENS = "contextWindowTokens";
    public static final String CONTEXT_UTILIZATION_RATIO = "contextUtilizationRatio";

    public static final String REASONING_STATUS = "status";
    public static final String REASONING_NOTE = "note";
    public static final String REASONING_CONTENT = "content";
    public static final String REASONING_CONTENT_TRUNCATED = "contentTruncated";

    public static final String TIMING_STARTED_AT = "startedAt";
    public static final String TIMING_FINISHED_AT = "finishedAt";
    public static final String TIMING_DURATION_MS = "durationMs";

    public static final String PAYLOAD_CONTENT_LENGTH = "contentLength";
    public static final String PAYLOAD_CONTENT_TRUNCATED = "contentTruncated";

    public static final String USAGE_PROMPT_TOKENS = "promptTokens";
    public static final String USAGE_COMPLETION_TOKENS = "completionTokens";
    public static final String USAGE_TOTAL_TOKENS = "totalTokens";

    public static final String ERROR_TYPE = "type";
    public static final String ERROR_MESSAGE = "message";
    public static final String ERROR_TRUNCATED = "truncated";

    public static final String TOOL_NAME = "toolName";
    public static final String TOOL_TYPE = "toolType";
    public static final String TOOL_SERVER_NAME = "serverName";
    public static final String TOOL_INPUT_PREVIEW = "inputPreview";
    public static final String TOOL_OUTPUT_PREVIEW = "outputPreview";
    public static final String TOOL_DURATION_MS = "durationMs";
    public static final String TOOL_SUCCESS = "success";

    // ---- react 过程级观测 ----
    public static final String REACT_EXIT_REASON = "exitReason";
    public static final String REACT_STEP_COUNT = "stepCount";
    public static final String REACT_TOTAL_TOOL_CALLS = "totalToolCalls";
    public static final String REACT_MAX_STEPS = "maxSteps";
    public static final String REACT_MAX_TOOL_CALLS = "maxToolCalls";
    public static final String REACT_LLM_TIMEOUT_MS = "llmTimeoutMs";
    public static final String REACT_STARTED_AT = "startedAt";
    public static final String REACT_FINISHED_AT = "finishedAt";
    public static final String REACT_DURATION_MS = "durationMs";
    public static final String REACT_ERROR_MESSAGE = "errorMessage";
    public static final String REACT_STEPS = "steps";
    public static final String REACT_STEP_INDEX = "index";
    public static final String REACT_STEP_TOOL_CALL_COUNT = "toolCallCount";
    public static final String REACT_STEP_TOOL_NAMES = "toolNames";
    public static final String REACT_STEP_FAILED_TOOL_COUNT = "failedToolCount";
    public static final String REACT_STEP_HAD_REASONING = "hadReasoning";
    public static final String REACT_STEP_CONTENT_LENGTH = "contentLength";

}
