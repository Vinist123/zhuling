package com.vinist.domain.agent.adapter.port;

import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM 调用端口接口
 *
 * <p>Domain 层定义，Infrastructure 层实现
 * 负责封装 Spring AI 的 ChatClient 能力
 *
 * <p>注意：domain 模块只依赖 spring-ai-model（轻量接口包），
 * ChatClient 的具体类型在 infrastructure 实现中解析
 */
public interface IChatModelPort {

    /**
     * 同步调用
     *
     * @param systemPrompt 系统提示词（来自 agent-desc），可为 null
     * @param history      历史消息列表（不含当前用户消息）
     * @param userMessage  当前用户消息
     * @return 响应文本
     */
    String call(String systemPrompt, List<MessageEntity> history, String userMessage);

    /**
     * 流式调用
     *
     * @param systemPrompt 系统提示词（来自 agent-desc），可为 null
     * @param history      历史消息列表（不含当前用户消息）
     * @param userMessage  当前用户消息
     * @param telemetry    当前轮次遥测上下文（流式模式下 Reactor 线程无法访问 ThreadLocal，需通过参数传递）
     * @return Flux 流式响应
     */
    Flux<String> stream(String systemPrompt, List<MessageEntity> history, String userMessage, TurnTelemetry telemetry);

}
