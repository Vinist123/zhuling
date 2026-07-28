package com.vinist.caseflow.observability;

import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.model.MessageMetadataKeys;
import com.vinist.domain.agent.model.MessageMetadataPolicy;
import com.vinist.domain.agent.model.ModuleConfig;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.model.telemetry.ChatContextMetrics;
import com.vinist.domain.agent.model.telemetry.ChatUsageMetrics;
import com.vinist.domain.agent.model.telemetry.ReasoningContentStatus;
import com.vinist.domain.agent.model.telemetry.ToolCallTelemetry;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import com.vinist.domain.agent.service.IMessageMetadataCollector;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息 metadata 收集器实现
 *
 * <p>同步模式：通过 ThreadLocal 获取 telemetry（currentTurn()）
 * <p>流式模式：通过参数直接传入 telemetry 引用（不依赖 ThreadLocal）
 */
@Service
public class MessageMetadataCollectorImpl implements IMessageMetadataCollector {

    private static final String SCHEMA_VERSION_V1 = "v1";
    private static final String RECORD_TYPE_CHAT_OBSERVABILITY = "chat-observability";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";

    private final IAgentRuntimeRegistry agentRuntimeRegistry;
    private final ITurnTelemetryService turnTelemetryService;

    public MessageMetadataCollectorImpl(IAgentRuntimeRegistry agentRuntimeRegistry,
                                        ITurnTelemetryService turnTelemetryService) {
        this.agentRuntimeRegistry = agentRuntimeRegistry;
        this.turnTelemetryService = turnTelemetryService;
    }

    // ---- 同步模式（从 ThreadLocal 获取 telemetry） ----

    @Override
    public Map<String, Object> collectUserMessageMetadata(SessionEntity session, String content, boolean stream) {
        return collectUserMessageMetadata(session, content, stream, turnTelemetryService.currentTurn());
    }

    @Override
    public Map<String, Object> collectAssistantMessageMetadata(SessionEntity session,
                                                               String content,
                                                               boolean stream,
                                                               long startedAtMs,
                                                               Throwable error) {
        return collectAssistantMessageMetadata(session, content, stream, startedAtMs, error, turnTelemetryService.currentTurn());
    }

    // ---- 流式模式（直接传入 telemetry 引用） ----

    @Override
    public Map<String, Object> collectUserMessageMetadata(SessionEntity session, String content, boolean stream, TurnTelemetry telemetry) {
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = createBaseMetadata(session, "user", stream, telemetry);
        metadata.put(MessageMetadataKeys.STATUS, STATUS_SUCCESS);
        metadata.put(MessageMetadataKeys.TIMING, createTiming(now, now));
        metadata.put(MessageMetadataKeys.PAYLOAD, createPayload(content));
        metadata.put(MessageMetadataKeys.USAGE, createUsage(telemetry));
        metadata.put(MessageMetadataKeys.ERROR, null);
        metadata.put(MessageMetadataKeys.EXT, new LinkedHashMap<>());
        return metadata;
    }

    @Override
    public Map<String, Object> collectAssistantMessageMetadata(SessionEntity session,
                                                               String content,
                                                               boolean stream,
                                                               long startedAtMs,
                                                               Throwable error,
                                                               TurnTelemetry telemetry) {
        Instant startedAt = Instant.ofEpochMilli(startedAtMs);
        Instant finishedAt = Instant.now();
        LinkedHashMap<String, Object> metadata = createBaseMetadata(session, "assistant", stream, telemetry);
        metadata.put(MessageMetadataKeys.STATUS, error == null ? STATUS_SUCCESS : STATUS_ERROR);
        metadata.put(MessageMetadataKeys.TIMING, createTiming(startedAt, finishedAt));
        metadata.put(MessageMetadataKeys.PAYLOAD, createPayload(content));
        metadata.put(MessageMetadataKeys.USAGE, createUsage(telemetry));
        metadata.put(MessageMetadataKeys.ERROR, error == null ? null : createError(error));
        metadata.put(MessageMetadataKeys.EXT, new LinkedHashMap<>());
        return metadata;
    }

    private LinkedHashMap<String, Object> createBaseMetadata(SessionEntity session, String role, boolean stream, TurnTelemetry telemetry) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        AgentConfigModel agentConfig = session != null && session.getTargetType() == ChatTargetType.AGENT
                ? agentRuntimeRegistry.getRequired(session.getTargetId()).getConfig() : null;
        ModuleConfig moduleConfig = agentConfig != null ? agentConfig.getModule() : null;
        ModuleConfig.ChatModelConfig chatModelConfig = moduleConfig != null ? moduleConfig.getChatModel() : null;
        ModuleConfig.AiApiConfig aiApiConfig = moduleConfig != null ? moduleConfig.getAiApi() : null;
        ModuleConfig.ObservabilityConfig observabilityConfig = moduleConfig != null ? moduleConfig.getObservability() : null;

        metadata.put(MessageMetadataKeys.SCHEMA_VERSION, SCHEMA_VERSION_V1);
        metadata.put(MessageMetadataKeys.RECORD_TYPE, RECORD_TYPE_CHAT_OBSERVABILITY);
        metadata.put(MessageMetadataKeys.MESSAGE_ROLE, role);
        metadata.put(MessageMetadataKeys.SESSION_ID, session != null ? session.getId() : null);
        metadata.put(MessageMetadataKeys.TARGET_ID, session != null ? session.getTargetId() : null);
        metadata.put(MessageMetadataKeys.TARGET_TYPE, session != null ? session.getTargetType() : null);
        metadata.put(MessageMetadataKeys.AGENT_ID,
                session != null && session.getTargetType() == ChatTargetType.AGENT ? session.getTargetId() : null);
        metadata.put(MessageMetadataKeys.STREAM, stream);
        metadata.put(MessageMetadataKeys.MODEL, createModel(chatModelConfig, aiApiConfig));
        metadata.put(MessageMetadataKeys.OBSERVABILITY, createObservability(observabilityConfig));
        metadata.put(MessageMetadataKeys.TRACE, createTrace(telemetry));
        metadata.put(MessageMetadataKeys.CONTEXT, createContext(telemetry));
        metadata.put(MessageMetadataKeys.REASONING,
                Boolean.FALSE.equals(observabilityConfig != null ? observabilityConfig.getReasoningContentEnabled() : null)
                        ? null : createReasoning(telemetry));
        metadata.put(MessageMetadataKeys.TOOL_CALLS,
                Boolean.FALSE.equals(observabilityConfig != null ? observabilityConfig.getToolCallEnabled() : null)
                        ? null : createToolCalls(telemetry));
        return metadata;
    }

    private Map<String, Object> createModel(ModuleConfig.ChatModelConfig chatModelConfig,
                                            ModuleConfig.AiApiConfig aiApiConfig) {
        LinkedHashMap<String, Object> model = new LinkedHashMap<>();
        model.put(MessageMetadataKeys.MODEL_NAME,
                truncate(chatModelConfig != null ? chatModelConfig.getModel() : null,
                        MessageMetadataPolicy.MAX_MODEL_NAME_LENGTH));
        model.put(MessageMetadataKeys.MODEL_BASE_URL,
                truncate(aiApiConfig != null ? aiApiConfig.getBaseUrl() : null,
                        MessageMetadataPolicy.MAX_MODEL_BASE_URL_LENGTH));
        return model;
    }

    private Map<String, Object> createObservability(ModuleConfig.ObservabilityConfig observabilityConfig) {
        LinkedHashMap<String, Object> observability = new LinkedHashMap<>();
        observability.put(MessageMetadataKeys.OBS_REACT_ENABLED,
                observabilityConfig != null ? observabilityConfig.getReactEnabled() : null);
        observability.put(MessageMetadataKeys.OBS_REASONING_CONTENT_ENABLED,
                observabilityConfig != null ? observabilityConfig.getReasoningContentEnabled() : null);
        observability.put(MessageMetadataKeys.OBS_TOOL_CALL_ENABLED,
                observabilityConfig != null ? observabilityConfig.getToolCallEnabled() : null);
        return observability;
    }

    private Map<String, Object> createTiming(Instant startedAt, Instant finishedAt) {
        LinkedHashMap<String, Object> timing = new LinkedHashMap<>();
        timing.put(MessageMetadataKeys.TIMING_STARTED_AT, startedAt != null ? startedAt.toString() : null);
        timing.put(MessageMetadataKeys.TIMING_FINISHED_AT, finishedAt != null ? finishedAt.toString() : null);
        timing.put(MessageMetadataKeys.TIMING_DURATION_MS,
                startedAt != null && finishedAt != null ? Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli()) : null);
        return timing;
    }

    private Map<String, Object> createPayload(String content) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put(MessageMetadataKeys.PAYLOAD_CONTENT_LENGTH, content != null ? content.length() : 0);
        payload.put(MessageMetadataKeys.PAYLOAD_CONTENT_TRUNCATED, false);
        return payload;
    }

    private Map<String, Object> createUsage(TurnTelemetry telemetry) {
        LinkedHashMap<String, Object> usage = new LinkedHashMap<>();
        ChatUsageMetrics usageMetrics = telemetry != null ? telemetry.getUsage() : null;
        usage.put(MessageMetadataKeys.USAGE_PROMPT_TOKENS, usageMetrics != null ? usageMetrics.getPromptTokens() : null);
        usage.put(MessageMetadataKeys.USAGE_COMPLETION_TOKENS, usageMetrics != null ? usageMetrics.getCompletionTokens() : null);
        usage.put(MessageMetadataKeys.USAGE_TOTAL_TOKENS, usageMetrics != null ? usageMetrics.getTotalTokens() : null);
        return usage;
    }

    private Map<String, Object> createError(Throwable error) {
        LinkedHashMap<String, Object> errorMap = new LinkedHashMap<>();
        String message = error.getMessage();
        boolean truncated = message != null && message.length() > MessageMetadataPolicy.MAX_ERROR_MESSAGE_LENGTH;

        errorMap.put(MessageMetadataKeys.ERROR_TYPE, error.getClass().getName());
        errorMap.put(MessageMetadataKeys.ERROR_MESSAGE,
                truncate(message, MessageMetadataPolicy.MAX_ERROR_MESSAGE_LENGTH));
        errorMap.put(MessageMetadataKeys.ERROR_TRUNCATED, truncated);
        return errorMap;
    }

    private Map<String, Object> createTrace(TurnTelemetry telemetry) {
        LinkedHashMap<String, Object> trace = new LinkedHashMap<>();
        trace.put(MessageMetadataKeys.TRACE_ID, telemetry != null ? telemetry.getTraceId() : null);
        trace.put(MessageMetadataKeys.TRACE_REQUEST_ID, telemetry != null ? telemetry.getRequestId() : null);
        trace.put(MessageMetadataKeys.TRACE_TURN_ID, telemetry != null ? telemetry.getTurnId() : null);
        return trace;
    }

    private Map<String, Object> createContext(TurnTelemetry telemetry) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        ChatContextMetrics contextMetrics = telemetry != null ? telemetry.getContext() : null;
        context.put(MessageMetadataKeys.CONTEXT_AVAILABLE_MESSAGE_COUNT,
                contextMetrics != null ? contextMetrics.getAvailableMessageCount() : null);
        context.put(MessageMetadataKeys.CONTEXT_INCLUDED_MESSAGE_COUNT,
                contextMetrics != null ? contextMetrics.getIncludedMessageCount() : null);
        context.put(MessageMetadataKeys.CONTEXT_PROMPT_CHARACTER_COUNT,
                contextMetrics != null ? contextMetrics.getPromptCharacterCount() : null);
        context.put(MessageMetadataKeys.CONTEXT_ESTIMATED_PROMPT_TOKENS,
                contextMetrics != null ? contextMetrics.getEstimatedPromptTokens() : null);
        context.put(MessageMetadataKeys.CONTEXT_WINDOW_TOKENS,
                contextMetrics != null ? contextMetrics.getContextWindowTokens() : null);
        context.put(MessageMetadataKeys.CONTEXT_UTILIZATION_RATIO,
                contextMetrics != null ? contextMetrics.getContextUtilizationRatio() : null);
        return context;
    }

    private Map<String, Object> createReasoning(TurnTelemetry telemetry) {
        LinkedHashMap<String, Object> reasoning = new LinkedHashMap<>();
        ReasoningContentStatus status = telemetry != null ? telemetry.getReasoningStatus() : null;
        reasoning.put(MessageMetadataKeys.REASONING_STATUS, status != null ? status.toValue() : null);
        reasoning.put(MessageMetadataKeys.REASONING_NOTE,
                truncate(telemetry != null ? telemetry.getReasoningNote() : null,
                        MessageMetadataPolicy.MAX_REASONING_NOTE_LENGTH));
        reasoning.put(MessageMetadataKeys.REASONING_CONTENT,
                truncate(telemetry != null ? telemetry.getReasoningContent() : null,
                        MessageMetadataPolicy.MAX_REASONING_CONTENT_LENGTH));
        reasoning.put(MessageMetadataKeys.REASONING_CONTENT_TRUNCATED,
                telemetry != null ? telemetry.getReasoningContentTruncated() : null);
        return reasoning;
    }

    private List<Map<String, Object>> createToolCalls(TurnTelemetry telemetry) {
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        if (telemetry == null || telemetry.getToolCalls() == null) {
            return toolCalls;
        }

        for (ToolCallTelemetry toolCall : telemetry.getToolCalls()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put(MessageMetadataKeys.TOOL_NAME, toolCall.getToolName());
            item.put(MessageMetadataKeys.TOOL_TYPE, toolCall.getToolType());
            item.put(MessageMetadataKeys.TOOL_SERVER_NAME, toolCall.getServerName());
            item.put(MessageMetadataKeys.TOOL_INPUT_PREVIEW, toolCall.getInputPreview());
            item.put(MessageMetadataKeys.TOOL_OUTPUT_PREVIEW, toolCall.getOutputPreview());
            item.put(MessageMetadataKeys.TOOL_DURATION_MS, toolCall.getDurationMs());
            item.put(MessageMetadataKeys.TOOL_SUCCESS, toolCall.getSuccess());
            item.put(MessageMetadataKeys.ERROR_TYPE, toolCall.getErrorType());
            item.put(MessageMetadataKeys.ERROR_MESSAGE, toolCall.getErrorMessage());
            toolCalls.add(item);
        }
        return toolCalls;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
