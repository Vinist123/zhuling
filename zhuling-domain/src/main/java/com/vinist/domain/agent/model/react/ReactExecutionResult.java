package com.vinist.domain.agent.model.react;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactExecutionResult {

    private String finalAnswer;

    private ReactExitReason exitReason;

    private int totalToolCalls;

    @Builder.Default
    private List<ReactStep> steps = List.of();

    private String errorMessage;

    /** 循环开始时间（由执行器在返回前标注） */
    private Instant startedAt;

    /** 循环结束时间（由执行器在返回前标注） */
    private Instant finishedAt;

}
