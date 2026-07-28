package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.telemetry.ConversationStreamEvent;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 单轮对话实时观测事件发布端口。
 */
public interface IConversationStreamEventPublisher {

    /**
     * 创建本轮事件通道。
     */
    void open(TurnTelemetry telemetry);

    /**
     * 获取本轮按序事件流。
     */
    Flux<ConversationStreamEvent> events(String turnId);

    /**
     * 发布一条事件。
     */
    void publish(TurnTelemetry telemetry,
                 ConversationStreamEventType type,
                 Map<String, Object> payload);

    /**
     * 从模型的累计 reasoning 快照中发布增量。
     */
    void publishReasoningDelta(TurnTelemetry telemetry, String cumulativeReasoning);

    /**
     * 兜底关闭本轮事件通道。
     */
    void complete(TurnTelemetry telemetry);

}
