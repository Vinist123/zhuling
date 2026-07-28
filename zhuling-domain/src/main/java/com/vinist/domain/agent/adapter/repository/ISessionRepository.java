package com.vinist.domain.agent.adapter.repository;

import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.entity.SessionPage;
import com.vinist.domain.agent.model.ChatTargetType;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 会话仓储接口
 * 
 * <p>返回 Mono/Flux 是因为会话操作可能被 WebFlux 调用
 */
public interface ISessionRepository {

    /**
     * 保存会话
     */
    Mono<String> save(SessionEntity session);

    /**
     * 根据 ID 查询
     */
    Mono<SessionEntity> getById(String sessionId);

    /**
     * 根据对话目标和 User ID 查询
     */
    Mono<SessionEntity> getByTargetIdAndTypeAndUserId(String targetId, ChatTargetType targetType, String userId);

    /**
     * 获取用户的所有会话
     */
    Mono<List<SessionEntity>> listByUserId(String userId);

    /**
     * 按用户筛选、分页查询会话。
     */
    Mono<SessionPage> listPageByUserId(String userId,
                                       String targetId,
                                       String status,
                                       int page,
                                       int pageSize);

    /**
     * 更新会话
     */
    Mono<Void> update(SessionEntity session);

    /**
     * 删除会话
     */
    Mono<Void> deleteById(String sessionId);

    /**
     * 增加消息计数
     */
    Mono<Void> incrementMessageCount(String sessionId);

}
