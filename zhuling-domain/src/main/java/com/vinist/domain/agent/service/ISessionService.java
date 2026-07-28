package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.entity.SessionPage;
import com.vinist.domain.agent.model.ChatTargetType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会话服务接口
 * 
 * <p>领域服务，负责会话生命周期管理
 */
public interface ISessionService {

    /**
     * 创建新会话
     */
    Mono<String> createSession(String targetId, ChatTargetType targetType, String userId, String title);

    /**
     * 获取会话信息（带缓存）
     */
    Mono<SessionEntity> getSession(String sessionId);

    /**
     * 按用户查询会话分页列表。
     */
    Mono<SessionPage> listSessions(String userId, String targetId, String status, int page, int pageSize);

    /**
     * 关闭会话
     */
    Mono<Void> closeSession(String sessionId);

    /**
     * 保存消息
     */
    Mono<String> saveMessage(MessageEntity message);

    /**
     * 查询会话下的消息
     */
    Flux<MessageEntity> getMessages(String sessionId);

}
