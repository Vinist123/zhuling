package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.telemetry.ReasoningContentStatus;
import com.vinist.domain.agent.model.telemetry.ToolCallTelemetry;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;

/**
 * 单轮对话遥测服务
 */
public interface ITurnTelemetryService {

    /**
     * 创建新的单轮遥测上下文。
     */
    TurnTelemetry startTurn(String sessionId, String agentId, boolean stream, int availableMessageCount, String prompt);

    /**
     * 更新本轮实际送入模型的上下文统计。
     */
    void updateContext(TurnTelemetry telemetry, int availableMessageCount, int includedMessageCount, String prompt, Integer contextWindowTokens);

    /**
     * 绑定当前线程使用的对话上下文。
     */
    void bindCurrentTurn(TurnTelemetry telemetry);

    /**
     * 获取当前线程绑定的对话上下文。
     */
    TurnTelemetry currentTurn();

    /**
     * 清理当前线程上下文。
     */
    void clearCurrentTurn();

    /**
     * 记录 usage 指标。
     */
    void recordUsage(Long promptTokens, Long completionTokens, Long totalTokens);

    /**
     * 记录 reasoning 能力状态。
     */
    void recordReasoningCapability(ReasoningContentStatus status, String note);

    /**
     * 记录 reasoning 原文（最佳努力）。
     */
    void recordReasoningContent(String reasoningContent);

    /**
     * 追加工具调用遥测。
     */
    void appendToolCall(ToolCallTelemetry telemetry);

    // ---- 带 TurnTelemetry 参数的重载（流式模式专用，不依赖 ThreadLocal） ----

    /**
     * 直接向指定 telemetry 记录 usage 指标（流式模式专用）。
     */
    void recordUsage(TurnTelemetry telemetry, Long promptTokens, Long completionTokens, Long totalTokens);

    /**
     * 直接向指定 telemetry 记录 reasoning 能力状态（流式模式专用）。
     */
    void recordReasoningCapability(TurnTelemetry telemetry, ReasoningContentStatus status, String note);

    /**
     * 直接向指定 telemetry 记录 reasoning 原文（流式模式专用）。
     */
    void recordReasoningContent(TurnTelemetry telemetry, String reasoningContent);

    /**
     * 直接向指定 telemetry 追加工具调用遥测（流式模式专用）。
     */
    void appendToolCall(TurnTelemetry telemetry, ToolCallTelemetry toolCallTelemetry);

}
