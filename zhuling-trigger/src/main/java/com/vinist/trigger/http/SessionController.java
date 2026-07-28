package com.vinist.trigger.http;

import com.vinist.api.dto.CreateSessionRequestDTO;
import com.vinist.api.response.Response;
import com.vinist.api.response.SessionMessageVO;
import com.vinist.api.response.SessionPageVO;
import com.vinist.api.response.SessionVO;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.model.entity.SessionEntity;
import com.vinist.domain.agent.model.entity.SessionPage;
import com.vinist.domain.agent.service.ISessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 会话管理控制器
 * 
 * <p>提供会话创建、查询、关闭等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final ISessionService sessionService;

    public SessionController(ISessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 创建新会话
     */
    @PostMapping("/create")
    public Mono<Response<String>> createSession(@RequestBody CreateSessionRequestDTO request) {
        log.info("收到创建会话请求: targetType={}, targetId={}, userId={}",
                request.getTargetType(), request.getTargetId(), request.getUserId());
        return sessionService.createSession(request.getTargetId(), parseTargetType(request.getTargetType()),
                        request.getUserId(), request.getTitle())
                .map(sessionId -> {
                    Response<String> response = new Response<>();
                    response.setCode("00000");
                    response.setInfo("success");
                    response.setData(sessionId);
                    return response;
                });
    }

    /**
     * 分页查询当前模拟用户的会话。
     */
    @GetMapping
    public Mono<Response<SessionPageVO>> listSessions(@RequestParam("userId") String userId,
                                                       @RequestParam(value = "targetId", required = false) String targetId,
                                                       @RequestParam(value = "status", required = false) String status,
                                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                                       @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        return sessionService.listSessions(userId, targetId, status, page, pageSize)
                .map(this::buildSessionPageResponse);
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/{sessionId}")
    public Mono<Response<SessionVO>> getSession(@PathVariable("sessionId") String sessionId) {
        return sessionService.getSession(sessionId)
                .flatMap(session -> sessionService.getMessages(sessionId)
                        .collectList()
                        .map(messages -> {
                            SessionVO.MessageSummaryVO lastMessage = null;
                            if (!messages.isEmpty()) {
                                var latest = messages.get(messages.size() - 1);
                                lastMessage = SessionVO.MessageSummaryVO.builder()
                                        .id(latest.getId())
                                        .role(latest.getRole())
                                        .content(latest.getContent())
                                        .metadata(latest.getMetadata())
                                        .build();
                            }

                            SessionVO vo = toSessionVO(session, lastMessage);

                            Response<SessionVO> response = new Response<>();
                            response.setCode("00000");
                            response.setInfo("success");
                            response.setData(vo);
                            return response;
                        }))
                .defaultIfEmpty(buildNotFoundResponse());
    }

    /**
     * 关闭会话
     */
    @PostMapping("/{sessionId}/close")
    public Mono<Response<String>> closeSession(@PathVariable("sessionId") String sessionId) {
        return sessionService.closeSession(sessionId)
                .then(Mono.just(buildSuccessResponse(sessionId)));
    }

    /**
     * 获取会话的完整消息列表
     */
    @GetMapping("/{sessionId}/messages")
    public Mono<Response<List<SessionMessageVO>>> getSessionMessages(@PathVariable("sessionId") String sessionId) {
        return sessionService.getSession(sessionId)
                .flatMap(session -> sessionService.getMessages(sessionId)
                        .map(message -> SessionMessageVO.builder()
                                .id(message.getId())
                                .sessionId(message.getSessionId())
                                .role(message.getRole())
                                .content(message.getContent())
                                .metadata(message.getMetadata())
                                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toString() : null)
                                .build())
                        .collectList()
                        .map(messages -> {
                            Response<List<SessionMessageVO>> response = new Response<>();
                            response.setCode("00000");
                            response.setInfo("success");
                            response.setData(messages);
                            return response;
                        }))
                .defaultIfEmpty(buildMessagesNotFoundResponse());
    }

    private Response<SessionVO> buildNotFoundResponse() {
        Response<SessionVO> response = new Response<>();
        response.setCode("00001");
        response.setInfo("session not found");
        response.setData(null);
        return response;
    }

    private Response<SessionPageVO> buildSessionPageResponse(SessionPage page) {
        SessionPageVO pageVO = SessionPageVO.builder()
                .items(page.items().stream().map(session -> toSessionVO(session, null)).toList())
                .page(page.page())
                .pageSize(page.pageSize())
                .total(page.total())
                .build();
        Response<SessionPageVO> response = new Response<>();
        response.setCode("00000");
        response.setInfo("success");
        response.setData(pageVO);
        return response;
    }

    private SessionVO toSessionVO(SessionEntity session, SessionVO.MessageSummaryVO lastMessage) {
        return SessionVO.builder()
                .id(session.getId())
                .targetId(session.getTargetId())
                .targetType(session.getTargetType() != null ? session.getTargetType().name() : null)
                .userId(session.getUserId())
                .title(session.getTitle())
                .status(session.getStatus())
                .messageCount(session.getMessageCount())
                .createdAt(session.getCreatedAt() != null ? session.getCreatedAt().toString() : null)
                .updatedAt(session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null)
                .lastMessage(lastMessage)
                .build();
    }

    private Response<String> buildSuccessResponse(String sessionId) {
        Response<String> response = new Response<>();
        response.setCode("00000");
        response.setInfo("success");
        response.setData(sessionId);
        return response;
    }

    private Response<List<SessionMessageVO>> buildMessagesNotFoundResponse() {
        Response<List<SessionMessageVO>> response = new Response<>();
        response.setCode("00001");
        response.setInfo("session not found");
        response.setData(null);
        return response;
    }

    private ChatTargetType parseTargetType(String value) {
        return value == null || value.isBlank() ? ChatTargetType.AGENT : ChatTargetType.valueOf(value.toUpperCase());
    }

}
