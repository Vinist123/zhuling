package com.vinist.domain.agent.model.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下文占用指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatContextMetrics {

    /**
     * 当前会话下已存在的历史消息条数。
     */
    private Integer availableMessageCount;

    /**
     * 当前实际送给模型的消息条数。
     */
    private Integer includedMessageCount;

    /**
     * 当前 user prompt 的字符数。
     */
    private Integer promptCharacterCount;

    /**
     * 估算 prompt token 数。
     */
    private Integer estimatedPromptTokens;

    /**
     * 模型上下文窗口大小；未知时允许为空。
     */
    private Integer contextWindowTokens;

    /**
     * 估算上下文占用比例；未知时允许为空。
     */
    private Double contextUtilizationRatio;

}
