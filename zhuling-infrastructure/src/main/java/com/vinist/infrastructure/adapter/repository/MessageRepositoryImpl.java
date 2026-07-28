package com.vinist.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinist.domain.agent.adapter.repository.IMessageRepository;
import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.infrastructure.dao.MessageDao;
import com.vinist.infrastructure.dao.po.MessagePO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息仓储实现
 * 
 * <p>使用阻塞式 MyBatis-Plus，通过专用线程池执行
 */
@Slf4j
@Repository
public class MessageRepositoryImpl implements IMessageRepository {

    private final MessageDao messageDao;
    private final ObjectMapper objectMapper;

    public MessageRepositoryImpl(MessageDao messageDao) {
        this.messageDao = messageDao;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Mono<String> save(MessageEntity message) {
        return Mono.fromCallable(() -> {
            MessagePO po = convertToPO(message);
            messageDao.insert(po);
            log.info("保存消息: id={}, role={}", po.getId(), po.getRole());
            return po.getId();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<MessageEntity> getById(String messageId) {
        return Mono.fromCallable(() -> {
            MessagePO po = messageDao.selectById(messageId);
            return convertToEntity(po);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<MessageEntity> listBySessionId(String sessionId) {
        return Mono.fromCallable(() -> {
            List<MessagePO> pos = this.selectBySessionId(sessionId);
            return pos.stream().map(this::convertToEntity).collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable);
    }

    private List<MessagePO> selectBySessionId(String sessionId) {
        LambdaQueryWrapper<MessagePO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(MessagePO::getSessionId, sessionId);
        lqw.orderByAsc(MessagePO::getCreatedAt);
        return messageDao.selectList(lqw);
    }

    @Override
    public Mono<Void> batchSave(List<MessageEntity> messages) {
        return Mono.fromRunnable(() -> {
            for (MessageEntity msg : messages) {
                MessagePO po = convertToPO(msg);
                messageDao.insert(po);
            }
            log.info("批量保存消息: count={}", messages.size());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteBySessionId(String sessionId) {
        return Mono.fromRunnable(() -> {
            this.delBySessionId(sessionId);
            log.info("删除会话消息: sessionId={}", sessionId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void delBySessionId(String sessionId) {
        LambdaQueryWrapper<MessagePO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(MessagePO::getSessionId, sessionId);
        messageDao.delete(lqw);
    }

    @Override
    public Mono<Void> updateMetadata(String messageId, Map<String, Object> metadata) {
        return Mono.fromRunnable(() -> {
            MessagePO po = messageDao.selectById(messageId);
            if (po != null) {
                try {
                    po.setMetadata(objectMapper.writeValueAsString(metadata));
                    messageDao.updateById(po);
                    log.info("更新消息 metadata: messageId={}", messageId);
                } catch (Exception e) {
                    log.error("序列化 metadata 失败", e);
                }
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private MessagePO convertToPO(MessageEntity entity) {
        if (entity == null) return null;
        try {
            String metadataJson = null;
            if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
                metadataJson = objectMapper.writeValueAsString(entity.getMetadata());
            }
            return MessagePO.builder()
                    .id(entity.getId())
                    .sessionId(entity.getSessionId())
                    .role(entity.getRole())
                    .content(entity.getContent())
                    .metadata(metadataJson)
                    .createdAt(entity.getCreatedAt())
                    .build();
        } catch (Exception e) {
            log.error("序列化 metadata 失败", e);
            return MessagePO.builder()
                    .id(entity.getId())
                    .sessionId(entity.getSessionId())
                    .role(entity.getRole())
                    .content(entity.getContent())
                    .metadata("{}")
                    .createdAt(entity.getCreatedAt())
                    .build();
        }
    }

    private MessageEntity convertToEntity(MessagePO po) {
        if (po == null) return null;
        return MessageEntity.builder()
                .id(po.getId())
                .sessionId(po.getSessionId())
                .role(po.getRole())
                .content(po.getContent())
                .metadata(parseMetadata(po.getMetadata()))
                .createdAt(po.getCreatedAt())
                .build();
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            log.warn("解析 metadata 失败: {}", metadataJson, e);
            return Map.of();
        }
    }

}
