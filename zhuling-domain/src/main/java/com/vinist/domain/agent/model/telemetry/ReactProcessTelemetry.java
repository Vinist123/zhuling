package com.vinist.domain.agent.model.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * ReAct 执行过程级观测摘要。
 *
 * <p>承载循环退出原因、步数统计、预算上限、时序与单步摘要，作为纯数据载体由编排层写入
 * {@link TurnTelemetry}，再由消息 metadata 收集器渲染为 {@code react} 段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactProcessTelemetry {

    /** 循环退出原因（ReactExitReason#name()），如 COMPLETED / MAX_STEPS_REACHED */
    private String exitReason;

    /** 实际执行步数 */
    private int stepCount;

    /** 实际工具调用总数 */
    private int totalToolCalls;

    /** 预算上限：最大步数 */
    private Integer maxSteps;

    /** 预算上限：最大工具调用数 */
    private Integer maxToolCalls;

    /** 预算上限：单次 LLM 调用超时（毫秒） */
    private Long llmTimeoutMs;

    /** 循环开始时间 */
    private Instant startedAt;

    /** 循环结束时间 */
    private Instant finishedAt;

    /** 循环耗时（毫秒） */
    private Long durationMs;

    /** 错误信息（已截断） */
    private String errorMessage;

    /** 各步轻量摘要 */
    @Builder.Default
    private List<ReactStepSummary> steps = List.of();

}
