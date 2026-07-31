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
public class ReactExecutionContext {

    private ReactExecutionRequest request;

    private Instant startedAt;

    private int currentStep;

    private int totalToolCalls;

    @Builder.Default
    private List<ReactStep> completedSteps = List.of();

}
