package com.vinist.domain.agent.adapter.repository;

import com.vinist.domain.agent.model.entity.MessageEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 消息仓储接口
 */
public interface IMessageRepository {

    /**
     * 保存消息
     */
    Mono<String> save(MessageEntity message);

    /**
     * 根据 ID 查询
     */
    Mono<MessageEntity> getById(String messageId);

    /**
     * 根据会话 ID 查询所有消息
     */
    Flux<MessageEntity> listBySessionId(String sessionId);

    /**
     * 批量保存消息
     */
    Mono<Void> batchSave(List<MessageEntity> messages);

    /**
     * 删除会话的所有消息
     */
    Mono<Void> deleteBySessionId(String sessionId);

    /**
     * 更新消息的 metadata
     */
    Mono<Void> updateMetadata(String messageId, java.util.Map<String, Object> metadata);

}
