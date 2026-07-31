package com.vinist.domain.agent.model.react;

import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactExecutionRequest {

    private String agentId;

    private String systemPrompt;

    @Builder.Default
    private List<MessageEntity> history = List.of();

    private String userMessage;

    @Builder.Default
    private ReactExecutionPolicy policy = ReactExecutionPolicy.builder().build();

    /** 本轮遥测上下文（可选，用于发布 agent.step / agent.loop 过程事件） */
    private TurnTelemetry turnTelemetry;

}
