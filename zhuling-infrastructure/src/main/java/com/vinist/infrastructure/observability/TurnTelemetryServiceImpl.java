package com.vinist.infrastructure.observability;

import com.vinist.domain.agent.model.telemetry.ChatContextMetrics;
import com.vinist.domain.agent.model.telemetry.ChatUsageMetrics;
import com.vinist.domain.agent.model.telemetry.ReasoningContentStatus;
import com.vinist.domain.agent.model.telemetry.ToolCallTelemetry;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 单轮对话遥测服务实现。
 *
 * <p>当前阶段优先服务单轮调用级别的 telemetry 采集，为后续 ReAct 多步事件流预留结构。
 */
@Service
public class TurnTelemetryServiceImpl implements ITurnTelemetryService {

    private static final ThreadLocal<TurnTelemetry> CURRENT_TURN = new ThreadLocal<>();

    @Override
    public TurnTelemetry startTurn(String sessionId, String agentId, boolean stream, int availableMessageCount, String prompt) {
        int promptCharacterCount = prompt != null ? prompt.length() : 0;
        int estimatedPromptTokens = estimateTokens(promptCharacterCount);

        return TurnTelemetry.builder()
                .traceId(newId("trace"))
                .requestId(newId("req"))
                .turnId(newId("turn"))
                .sessionId(sessionId)
                .agentId(agentId)
                .stream(stream)
                .reasoningStatus(ReasoningContentStatus.UNSUPPORTED)
                .reasoningNote("初始化：尚未采集到 reasoning_content，等待 LLM 响应判定")
                .reasoningContent(null)
                .reasoningContentTruncated(false)
                .usage(ChatUsageMetrics.builder().build())
                .context(ChatContextMetrics.builder()
                        .availableMessageCount(Math.max(availableMessageCount, 0))
                        .includedMessageCount(Math.max(availableMessageCount, 0) + 1)
                        .promptCharacterCount(promptCharacterCount)
                        .estimatedPromptTokens(estimatedPromptTokens)
//                        .contextWindowTokens(null)
//                        .contextUtilizationRatio(null)
                        .build())
                .toolCalls(new ArrayList<>())
                .build();
    }

    @Override
    public void updateContext(TurnTelemetry telemetry,
                              int availableMessageCount,
                              int includedMessageCount,
                              String prompt,
                              Integer contextWindowTokens) {
        if (telemetry == null) {
            return;
        }
        int promptCharacterCount = prompt != null ? prompt.length() : 0;
        if (telemetry.getContext() == null) {
            telemetry.setContext(new ChatContextMetrics());
        }
        telemetry.getContext().setAvailableMessageCount(Math.max(availableMessageCount, 0));
        telemetry.getContext().setIncludedMessageCount(Math.max(includedMessageCount, 0));
        telemetry.getContext().setPromptCharacterCount(promptCharacterCount);
        telemetry.getContext().setEstimatedPromptTokens(estimateTokens(promptCharacterCount));

        if (contextWindowTokens != null && contextWindowTokens > 0) {
            telemetry.getContext().setContextWindowTokens(contextWindowTokens);
            int totalEstimated = estimateTokens(promptCharacterCount);
            telemetry.getContext().setContextUtilizationRatio(Math.min(1.0d, (double) totalEstimated / contextWindowTokens));
        }
    }

    @Override
    public void bindCurrentTurn(TurnTelemetry telemetry) {
        CURRENT_TURN.set(telemetry);
    }

    @Override
    public TurnTelemetry currentTurn() {
        return CURRENT_TURN.get();
    }

    @Override
    public void clearCurrentTurn() {
        CURRENT_TURN.remove();
    }

    @Override
    public void recordUsage(Long promptTokens, Long completionTokens, Long totalTokens) {
        recordUsage(currentTurn(), promptTokens, completionTokens, totalTokens);
    }

    @Override
    public void recordReasoningCapability(ReasoningContentStatus status, String note) {
        recordReasoningCapability(currentTurn(), status, note);
    }

    @Override
    public void recordReasoningContent(String reasoningContent) {
        recordReasoningContent(currentTurn(), reasoningContent);
    }

    @Override
    public void appendToolCall(ToolCallTelemetry telemetry) {
        appendToolCall(currentTurn(), telemetry);
    }

    // ---- 带 telemetry 参数的重载实现（流式模式专用，不依赖 ThreadLocal） ----

    @Override
    public void recordUsage(TurnTelemetry telemetry, Long promptTokens, Long completionTokens, Long totalTokens) {
        if (telemetry == null) {
            return;
        }

        ChatUsageMetrics usage = telemetry.getUsage();
        if (usage == null) {
            usage = new ChatUsageMetrics();
            telemetry.setUsage(usage);
        }

        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setTotalTokens(totalTokens);
    }

    @Override
    public void recordReasoningCapability(TurnTelemetry telemetry, ReasoningContentStatus status, String note) {
        if (telemetry == null) {
            return;
        }

        telemetry.setReasoningStatus(status);
        telemetry.setReasoningNote(note);
    }

    @Override
    public void recordReasoningContent(TurnTelemetry telemetry, String reasoningContent) {
        if (telemetry == null) {
            return;
        }

        if (reasoningContent == null || reasoningContent.isBlank()) {
            return;
        }

        boolean truncated = reasoningContent.length() > com.vinist.domain.agent.model.MessageMetadataPolicy.MAX_REASONING_CONTENT_LENGTH;
        String content = truncated
                ? reasoningContent.substring(0, com.vinist.domain.agent.model.MessageMetadataPolicy.MAX_REASONING_CONTENT_LENGTH)
                : reasoningContent;

        telemetry.setReasoningContent(content);
        telemetry.setReasoningContentTruncated(truncated);
    }

    @Override
    public void appendToolCall(TurnTelemetry telemetry, ToolCallTelemetry toolCallTelemetry) {
        if (telemetry == null || toolCallTelemetry == null) {
            return;
        }

        telemetry.getToolCalls().add(toolCallTelemetry);
    }

    private String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private int estimateTokens(int characterCount) {
        if (characterCount <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(characterCount / 4.0d));
    }

}
