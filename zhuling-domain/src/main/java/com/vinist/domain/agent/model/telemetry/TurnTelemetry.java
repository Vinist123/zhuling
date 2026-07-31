package com.vinist.domain.agent.model.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单轮对话遥测上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnTelemetry {

    private String traceId;

    private String requestId;

    private String turnId;

    private String sessionId;

    private String agentId;

    private Boolean stream;

    private ReasoningContentStatus reasoningStatus;

    private String reasoningNote;

    private String reasoningContent;

    private Boolean reasoningContentTruncated;

    private ChatUsageMetrics usage;

    private ChatContextMetrics context;

    @Builder.Default
    private List<ToolCallTelemetry> toolCalls = new ArrayList<>();

    /** ReAct 执行过程级观测摘要（仅当本轮走了 ReAct 内核时非空） */
    private ReactProcessTelemetry reactProcess;

    /** 是否允许发布 reasoning SSE 事件（null/true = 允许，false = 禁止） */
    private Boolean reasoningContentEnabled;

    /** 是否允许发布 tool call SSE 事件（null/true = 允许，false = 禁止） */
    private Boolean toolCallEnabled;

}
