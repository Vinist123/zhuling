package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.telemetry.ConversationStreamEvent;
import reactor.core.publisher.Flux;

/**
 * 聊天服务接口
 * 
 * <p>领域服务，负责单次对话的核心逻辑
 */
public interface IChatService {

    /**
     * 执行单次对话
     * 
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @return 对话结果
     */
    String chat(String sessionId, String message);

    /**
     * 执行流式对话
     * 
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @return Flux 版本化流式事件
     */
    Flux<ConversationStreamEvent> streamChat(String sessionId, String message);

}
