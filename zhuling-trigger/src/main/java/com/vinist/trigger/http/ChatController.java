package com.vinist.trigger.http;

import com.vinist.api.dto.ChatRequestDTO;
import com.vinist.api.response.ChatResponseVO;
import com.vinist.api.response.Response;
import com.vinist.domain.agent.model.telemetry.ConversationStreamEvent;
import com.vinist.domain.agent.service.IChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器
 * 
 * <p>提供同步和流式聊天接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final IChatService chatService;

    public ChatController(IChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 同步聊天接口
     */
    @PostMapping("/sync")
    public Response<ChatResponseVO> syncChat(@RequestBody ChatRequestDTO request) {
        log.info("收到同步聊天请求: sessionId={}, message={}", request.getSessionId(), request.getMessage());
        String content = chatService.chat(request.getSessionId(), request.getMessage());
        ChatResponseVO response = ChatResponseVO.builder()
                .sessionId(request.getSessionId())
                .content(content)
                .build();
        Response<ChatResponseVO> result = new Response<>();
        result.setCode("00000");
        result.setInfo("success");
        result.setData(response);
        return result;
    }

    /**
     * 流式聊天接口（SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ConversationStreamEvent>> streamChat(@RequestBody ChatRequestDTO request) {
        log.info("收到流式聊天请求: sessionId={}, message={}", request.getSessionId(), request.getMessage());
        return chatService.streamChat(request.getSessionId(), request.getMessage())
                .map(event -> ServerSentEvent.<ConversationStreamEvent>builder()
                        .event(event.type())
                        .data(event)
                        .build());
    }

}
