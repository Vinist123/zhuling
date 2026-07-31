package com.vinist.infrastructure.observability;

import com.vinist.domain.agent.model.MessageMetadataPolicy;
import com.vinist.domain.agent.model.telemetry.ToolCallTelemetry;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为 ToolCallback 补充统一遥测埋点。
 */
public class TelemetryToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String toolType;
    private final String serverName;
    private final ITurnTelemetryService turnTelemetryService;
    private final IConversationStreamEventPublisher eventPublisher;

    public TelemetryToolCallback(ToolCallback delegate,
                                 String toolType,
                                 String serverName,
                                 ITurnTelemetryService turnTelemetryService,
                                 IConversationStreamEventPublisher eventPublisher) {
        this.delegate = delegate;
        this.toolType = toolType;
        this.serverName = serverName;
        this.turnTelemetryService = turnTelemetryService;
        this.eventPublisher = eventPublisher;
    }

    public ToolCallback getDelegate() {
        return delegate;
    }

    public String getToolType() {
        return toolType;
    }

    public String getServerName() {
        return serverName;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return execute(toolInput, null);
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        return execute(toolInput, toolContext);
    }

    private String execute(String toolInput, @Nullable ToolContext toolContext) {
        long startedAt = System.currentTimeMillis();
        TurnTelemetry turnTelemetry = turnTelemetryService.currentTurn();
        boolean publishToolEvents = turnTelemetry == null || !Boolean.FALSE.equals(turnTelemetry.getToolCallEnabled());
        if (publishToolEvents) {
            eventPublisher.publish(turnTelemetry, ConversationStreamEventType.TOOL_STARTED,
                    buildStartedPayload(toolInput));
        }
        try {
            String result = delegate.call(toolInput, toolContext);
            ToolCallTelemetry telemetry = appendTelemetry(
                    turnTelemetry, toolInput, result, System.currentTimeMillis() - startedAt, true, null);
            if (publishToolEvents) {
                eventPublisher.publish(turnTelemetry, ConversationStreamEventType.TOOL_COMPLETED,
                        buildCompletedPayload(telemetry));
            }
            return result;
        } catch (RuntimeException ex) {
            ToolCallTelemetry telemetry = appendTelemetry(
                    turnTelemetry, toolInput, null, System.currentTimeMillis() - startedAt, false, ex);
            if (publishToolEvents) {
                eventPublisher.publish(turnTelemetry, ConversationStreamEventType.TOOL_COMPLETED,
                        buildCompletedPayload(telemetry));
            }
            throw ex;
        }
    }

    private ToolCallTelemetry appendTelemetry(TurnTelemetry turnTelemetry,
                                              String toolInput,
                                              String result,
                                              long durationMs,
                                              boolean success,
                                              RuntimeException error) {
        ToolCallTelemetry telemetry = ToolCallTelemetry.builder()
                .toolName(delegate.getToolDefinition().name())
                .toolType(toolType)
                .serverName(serverName)
                .inputPreview(truncate(toolInput, MessageMetadataPolicy.MAX_TOOL_PREVIEW_LENGTH))
                .outputPreview(truncate(result, MessageMetadataPolicy.MAX_TOOL_PREVIEW_LENGTH))
                .durationMs(durationMs)
                .success(success)
                .errorType(error != null ? error.getClass().getName() : null)
                .errorMessage(error != null ? truncate(error.getMessage(), MessageMetadataPolicy.MAX_ERROR_MESSAGE_LENGTH) : null)
                .build();
        turnTelemetryService.appendToolCall(turnTelemetry, telemetry);
        return telemetry;
    }

    private Map<String, Object> buildStartedPayload(String toolInput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", delegate.getToolDefinition().name());
        payload.put("toolType", toolType);
        payload.put("serverName", serverName);
        payload.put("inputPreview", truncate(toolInput, MessageMetadataPolicy.MAX_TOOL_PREVIEW_LENGTH));
        return payload;
    }

    private Map<String, Object> buildCompletedPayload(ToolCallTelemetry telemetry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", telemetry.getToolName());
        payload.put("toolType", telemetry.getToolType());
        payload.put("serverName", telemetry.getServerName());
        payload.put("inputPreview", telemetry.getInputPreview());
        payload.put("outputPreview", telemetry.getOutputPreview());
        payload.put("durationMs", telemetry.getDurationMs());
        payload.put("success", telemetry.getSuccess());
        payload.put("errorType", telemetry.getErrorType());
        payload.put("error", telemetry.getErrorMessage());
        return payload;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
