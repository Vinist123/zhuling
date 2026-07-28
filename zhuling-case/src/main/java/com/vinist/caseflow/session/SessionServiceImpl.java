package com.vinist.caseflow.session;

import com.vinist.domain.agent.adapter.repository.IMessageRepository;
import com.vinist.domain.agent.adapter.repository.ISessionRepository;
import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.entity.SessionPage;
import com.vinist.domain.agent.model.ChatTarget;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.service.IChatTargetService;
import com.vinist.domain.agent.service.ISessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话服务实现
 * 
 * <p>负责会话生命周期管理，包含内存缓存 + 数据库持久化
 */
@Slf4j
@Service
public class SessionServiceImpl implements ISessionService {

    private final ISessionRepository sessionRepository;
    private final IMessageRepository messageRepository;
    private final IChatTargetService chatTargetService;
    
    // 内存缓存：sessionId -> SessionEntity
    private final ConcurrentHashMap<String, SessionEntity> sessionCache = new ConcurrentHashMap<>();
    
    // TTL 配置（秒）
    private static final int CACHE_TTL_SECONDS = 3600;

    public SessionServiceImpl(ISessionRepository sessionRepository,
                              IMessageRepository messageRepository,
                              IChatTargetService chatTargetService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.chatTargetService = chatTargetService;
    }

    @Override
    public Mono<String> createSession(String targetId, ChatTargetType targetType, String userId, String title) {
        return Mono.fromCallable(() -> {
            ChatTargetType resolvedType = targetType != null ? targetType : ChatTargetType.AGENT;
            ChatTarget target = chatTargetService.getRequired(resolvedType, targetId);
            SessionEntity session = SessionEntity.builder()
                    .targetId(target.getId())
                    .targetType(resolvedType)
                    .userId(userId)
                    .title(title != null ? title : "新对话")
                    .status("active")
                    .messageCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            String sessionId = sessionRepository.save(session).block();
            log.info("创建会话: id={}, targetType={}, targetId={}, userId={}",
                    sessionId, resolvedType, targetId, userId);
            return sessionId;
        });
    }

    @Override
    public Mono<SessionEntity> getSession(String sessionId) {
        return Mono.defer(() -> {
            // 1. 先从内存缓存查找
            Optional<SessionEntity> cached = Optional.ofNullable(sessionCache.get(sessionId));
            if (cached.isPresent()) {
                log.debug("命中会话缓存: sessionId={}", sessionId);
                return Mono.just(cached.get());
            }
            
            // 2. 缓存 miss，回源数据库
            log.debug("会话缓存 miss，回源数据库: sessionId={}", sessionId);
            return sessionRepository.getById(sessionId)
                    .doOnSuccess(session -> {
                        if (session != null) {
                            sessionCache.put(sessionId, session);
                        }
                    });
        });
    }

    @Override
    public Mono<SessionPage> listSessions(String userId, String targetId, String status, int page, int pageSize) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new IllegalArgumentException("userId 不能为空"));
        }
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.min(Math.max(1, pageSize), 50);
        return sessionRepository.listPageByUserId(
                userId,
                normalizeOptionalFilter(targetId),
                normalizeOptionalFilter(status),
                normalizedPage,
                normalizedPageSize);
    }

    @Override
    public Mono<Void> closeSession(String sessionId) {
        return sessionRepository.update(SessionEntity.builder()
                .id(sessionId)
                .status("closed")
                .updatedAt(LocalDateTime.now())
                .build()).then();
    }

    /**
     * 保存消息到数据库
     */
    @Override
    public Mono<String> saveMessage(MessageEntity message) {
        return messageRepository.save(message)
                .flatMap(id -> sessionRepository.incrementMessageCount(message.getSessionId()).thenReturn(id))
                .doOnSuccess(id -> {
                    // 更新缓存中的消息计数
                    SessionEntity cached = sessionCache.get(message.getSessionId());
                    if (cached != null) {
                        cached.setMessageCount(cached.getMessageCount() + 1);
                        cached.setUpdatedAt(LocalDateTime.now());
                    }
                });
    }

    /**
     * 获取会话的所有消息
     */
    @Override
    public Flux<MessageEntity> getMessages(String sessionId) {
        return messageRepository.listBySessionId(sessionId);
    }

    /**
     * 清理过期缓存
     */
    public void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        sessionCache.entrySet().removeIf(entry -> {
            SessionEntity session = entry.getValue();
            if (session.getUpdatedAt() == null) return false;
            long elapsed = (now - session.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) / 1000;
            return elapsed > CACHE_TTL_SECONDS;
        });
        log.debug("清理过期会话缓存完成");
    }

    private String normalizeOptionalFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
