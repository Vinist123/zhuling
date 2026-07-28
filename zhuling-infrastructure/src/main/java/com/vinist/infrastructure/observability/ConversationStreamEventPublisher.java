package com.vinist.infrastructure.observability;

import com.vinist.domain.agent.model.telemetry.ConversationStreamEvent;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEventType;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Reactor Sink 的单轮事件发布器。
 */
@Service
public class ConversationStreamEventPublisher implements IConversationStreamEventPublisher {

    private static final String SCHEMA_VERSION = "v1";

    private final ConcurrentHashMap<String, TurnChannel> channels = new ConcurrentHashMap<>();

    @Override
    public void open(TurnTelemetry telemetry) {
        if (telemetry == null || telemetry.getTurnId() == null || telemetry.getTurnId().isBlank()) {
            throw new IllegalArgumentException("TurnTelemetry 缺少 turnId，无法创建事件通道");
        }
        channels.putIfAbsent(telemetry.getTurnId(), new TurnChannel());
    }

    @Override
    public Flux<ConversationStreamEvent> events(String turnId) {
        TurnChannel channel = channels.get(turnId);
        if (channel == null) {
            return Flux.error(new IllegalStateException("对话事件通道不存在: " + turnId));
        }
        return channel.sink.asFlux();
    }

    @Override
    public void publish(TurnTelemetry telemetry,
                        ConversationStreamEventType type,
                        Map<String, Object> payload) {
        if (telemetry == null || type == null || telemetry.getTurnId() == null) {
            return;
        }

        TurnChannel channel = channels.get(telemetry.getTurnId());
        if (channel == null) {
            return;
        }

        synchronized (channel) {
            if (channel.terminal) {
                return;
            }

            ConversationStreamEvent event = new ConversationStreamEvent(
                    SCHEMA_VERSION,
                    type.wireName(),
                    telemetry.getSessionId(),
                    telemetry.getTurnId(),
                    channel.sequence.incrementAndGet(),
                    Instant.now(),
                    payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload)));
            Sinks.EmitResult result = channel.sink.tryEmitNext(event);
            if (result.isFailure() && !type.isTerminal()) {
                return;
            }

            if (type.isTerminal()) {
                channel.terminal = true;
                channel.sink.tryEmitComplete();
                channels.remove(telemetry.getTurnId(), channel);
            }
        }
    }

    @Override
    public void publishReasoningDelta(TurnTelemetry telemetry, String cumulativeReasoning) {
        if (telemetry == null || cumulativeReasoning == null || cumulativeReasoning.isBlank()) {
            return;
        }

        TurnChannel channel = channels.get(telemetry.getTurnId());
        if (channel == null) {
            return;
        }

        String delta;
        synchronized (channel) {
            if (channel.terminal || cumulativeReasoning.equals(channel.reasoningSnapshot)) {
                return;
            }
            delta = channel.reasoningSnapshot != null && cumulativeReasoning.startsWith(channel.reasoningSnapshot)
                    ? cumulativeReasoning.substring(channel.reasoningSnapshot.length())
                    : cumulativeReasoning;
            channel.reasoningSnapshot = cumulativeReasoning;
        }

        if (delta.isBlank()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", delta);
        payload.put("status", telemetry.getReasoningStatus() != null
                ? telemetry.getReasoningStatus().toValue() : null);
        payload.put("note", telemetry.getReasoningNote());
        publish(telemetry, ConversationStreamEventType.REASONING_DELTA, payload);
    }

    @Override
    public void complete(TurnTelemetry telemetry) {
        if (telemetry == null || telemetry.getTurnId() == null) {
            return;
        }

        TurnChannel channel = channels.remove(telemetry.getTurnId());
        if (channel == null) {
            return;
        }

        synchronized (channel) {
            if (!channel.terminal) {
                channel.terminal = true;
                channel.sink.tryEmitComplete();
            }
        }
    }

    private static final class TurnChannel {

        private final Sinks.Many<ConversationStreamEvent> sink = Sinks.many().replay().limit(256);
        private final AtomicLong sequence = new AtomicLong();
        private boolean terminal;
        private String reasoningSnapshot;

    }

}
