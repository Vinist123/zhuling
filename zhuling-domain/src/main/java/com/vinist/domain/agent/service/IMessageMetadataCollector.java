package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;

import java.util.Map;

/**
 * 消息 metadata 收集器
 */
public interface IMessageMetadataCollector {

    /**
     * 构建用户消息 metadata（同步模式，从 ThreadLocal 获取 telemetry）
     */
    Map<String, Object> collectUserMessageMetadata(SessionEntity session, String content, boolean stream);

    /**
     * 构建助手消息 metadata（同步模式，从 ThreadLocal 获取 telemetry）
     */
    Map<String, Object> collectAssistantMessageMetadata(SessionEntity session,
                                                        String content,
                                                        boolean stream,
                                                        long startedAtMs,
                                                        Throwable error);

    /**
     * 构建用户消息 metadata（流式模式，直接传入 telemetry 引用）
     */
    Map<String, Object> collectUserMessageMetadata(SessionEntity session, String content, boolean stream, TurnTelemetry telemetry);

    /**
     * 构建助手消息 metadata（流式模式，直接传入 telemetry 引用）
     */
    Map<String, Object> collectAssistantMessageMetadata(SessionEntity session,
                                                        String content,
                                                        boolean stream,
                                                        long startedAtMs,
                                                        Throwable error,
                                                        TurnTelemetry telemetry);

}
