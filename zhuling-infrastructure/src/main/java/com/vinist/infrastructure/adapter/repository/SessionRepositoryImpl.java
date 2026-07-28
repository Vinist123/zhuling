package com.vinist.infrastructure.adapter.repository;

import ch.qos.logback.core.util.StringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vinist.domain.agent.adapter.repository.ISessionRepository;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.entity.SessionPage;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.infrastructure.dao.SessionDao;
import com.vinist.infrastructure.dao.po.SessionPO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话仓储实现
 * 
 * <p>使用阻塞式 MyBatis-Plus，通过专用线程池执行
 */
@Slf4j
@Repository
public class SessionRepositoryImpl implements ISessionRepository {

    private final SessionDao sessionDao;

    public SessionRepositoryImpl(SessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    @Override
    public Mono<String> save(SessionEntity session) {
        return Mono.fromCallable(() -> {
            SessionPO po = convertToPO(session);
            sessionDao.insert(po);
            log.info("保存会话: id={}", po.getId());
            return po.getId();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<SessionEntity> getById(String sessionId) {
        return Mono.fromCallable(() -> {
            SessionPO po = sessionDao.selectById(sessionId);
            return convertToEntity(po);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<SessionEntity> getByTargetIdAndTypeAndUserId(String targetId,
                                                             ChatTargetType targetType,
                                                             String userId) {
        return Mono.fromCallable(() -> {
            SessionPO po = this.selectByTargetIdAndTypeAndUserId(
                    targetId, targetType.name(), userId);
            return convertToEntity(po);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private SessionPO selectByTargetIdAndTypeAndUserId(String targetId,
                                                        String targetType,
                                                        String userId) {
        LambdaQueryWrapper<SessionPO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(SessionPO::getTargetId, targetId)
          .eq(SessionPO::getTargetType, targetType)
          .eq(SessionPO::getUserId, userId)
          .orderByDesc(SessionPO::getCreatedAt);
        return sessionDao.selectOne(lqw);
    }

    @Override
    public Mono<List<SessionEntity>> listByUserId(String userId) {
        return Mono.fromCallable(() -> {
            List<SessionPO> pos = this.selectByUserId(userId);
            return pos.stream().map(this::convertToEntity).collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<SessionPO> selectByUserId(String userId) {
        LambdaQueryWrapper<SessionPO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(SessionPO::getUserId, userId)
          .orderByDesc(SessionPO::getCreatedAt);
        return sessionDao.selectList(lqw);
    }

    @Override
    public Mono<SessionPage> listPageByUserId(String userId,
                                              String targetId,
                                              String status,
                                              int page,
                                              int pageSize) {
        return Mono.fromCallable(() -> {
            List<SessionEntity> items = this.selectPageByUserId(
                            userId, targetId, status, page, pageSize)
                    .stream()
                    .map(this::convertToEntity)
                    .collect(Collectors.toList());
            long total = this.countByUserId(userId, targetId, status);
            return new SessionPage(items, total, page, pageSize);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private long countByUserId(String userId, String targetId, String status) {
        LambdaQueryWrapper<SessionPO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(SessionPO::getUserId, userId)
            .eq(SessionPO::getTargetId, targetId)
            .eq(SessionPO::getStatus, status);
        return sessionDao.selectCount(lqw);
    }

    private List<SessionPO> selectPageByUserId(String userId,
                                                   String targetId,
                                                   String status,
                                                   int page,
                                                   int pageSize) {
        int offset = (page - 1) * pageSize;
        LambdaQueryWrapper<SessionPO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(SessionPO::getUserId, userId)
            .eq(StringUtils.isNotBlank(targetId), SessionPO::getTargetId, targetId)
            .eq(StringUtils.isNotBlank(status), SessionPO::getStatus, status)
            .orderByDesc(SessionPO::getCreatedAt)
            .last(String.format("limit %d, %d", offset, pageSize));
        return sessionDao.selectList(lqw);
    }

    @Override
    public Mono<Void> update(SessionEntity session) {
        return Mono.fromRunnable(() -> {
            SessionPO po = convertToPO(session);
            sessionDao.updateById(po);
            log.info("更新会话: id={}", po.getId());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteById(String sessionId) {
        return Mono.fromRunnable(() -> {
            sessionDao.deleteById(sessionId);
            log.info("删除会话: id={}", sessionId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> incrementMessageCount(String sessionId) {
        return Mono.fromRunnable(() -> {
            sessionDao.incrementMessageCount(sessionId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }


    private SessionPO convertToPO(SessionEntity entity) {
        if (entity == null) return null;
        return SessionPO.builder()
                .id(entity.getId())
                .targetId(entity.getTargetId())
                .targetType(entity.getTargetType() != null ? entity.getTargetType().name() : null)
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .messageCount(entity.getMessageCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private SessionEntity convertToEntity(SessionPO po) {
        if (po == null) return null;
        return SessionEntity.builder()
                .id(po.getId())
                .targetId(po.getTargetId())
                .targetType(parseTargetType(po.getTargetType()))
                .userId(po.getUserId())
                .title(po.getTitle())
                .status(po.getStatus())
                .messageCount(po.getMessageCount())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private ChatTargetType parseTargetType(String value) {
        return value == null || value.isBlank() ? ChatTargetType.AGENT : ChatTargetType.valueOf(value);
    }

}
