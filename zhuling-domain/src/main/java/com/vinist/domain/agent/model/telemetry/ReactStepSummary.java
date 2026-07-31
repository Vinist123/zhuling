package com.vinist.domain.agent.model.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ReAct 单步观测摘要。
 *
 * <p>仅保存轻量、稳定的观测字段（步序号、工具调用数、工具名、失败数、是否含推理、正文长度），
 * 不在 metadata 中重复持久化整段正文或工具出参，遵循 Phase 5 的 metadata 边界策略。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactStepSummary {

    /** 步序号，从 1 开始 */
    private int index;

    /** 该步模型请求的工具调用数（act） */
    private int toolCallCount;

    /** 该步执行的工具名列表（observe 维度） */
    @Builder.Default
    private List<String> toolNames = List.of();

    /** 该步执行失败的工具数 */
    private int failedToolCount;

    /** 该步模型响应是否包含 reasoning_content */
    private boolean hadReasoning;

    /** 该步模型正文长度（字符数） */
    private int contentLength;

}
