package com.vinist.domain.agent.model.telemetry;

import java.time.Instant;
import java.util.Map;

/**
 * 面向浏览器 SSE 的版本化单轮对话事件。
 */
public record ConversationStreamEvent(
        String schemaVersion,
        String type,
        String sessionId,
        String turnId,
        long sequence,
        Instant timestamp,
        Map<String, Object> payload) {
}
