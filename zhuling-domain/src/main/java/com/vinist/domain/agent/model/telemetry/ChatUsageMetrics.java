package com.vinist.domain.agent.model.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM usage 指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatUsageMetrics {

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;

}
