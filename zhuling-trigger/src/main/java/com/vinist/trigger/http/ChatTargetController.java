package com.vinist.trigger.http;

import com.vinist.api.response.ChatTargetVO;
import com.vinist.api.response.Response;
import com.vinist.domain.agent.model.ChatTarget;
import com.vinist.domain.agent.model.ChatTargetType;
import com.vinist.domain.agent.service.IChatTargetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-targets")
public class ChatTargetController {

    private final IChatTargetService chatTargetService;

    public ChatTargetController(IChatTargetService chatTargetService) {
        this.chatTargetService = chatTargetService;
    }

    @GetMapping
    public Response<List<ChatTargetVO>> listTargets() {
        List<ChatTargetVO> targets = chatTargetService.listTargets().stream()
                .map(this::toVO)
                .toList();
        return Response.<List<ChatTargetVO>>builder()
                .code("00000")
                .info("success")
                .data(targets)
                .build();
    }

    @GetMapping("/{type}/{id}")
    public Response<ChatTargetVO> getTarget(@PathVariable String type, @PathVariable String id) {
        ChatTarget target = chatTargetService.getRequired(parseType(type), id);
        return Response.<ChatTargetVO>builder()
                .code("00000")
                .info("success")
                .data(toVO(target))
                .build();
    }

    private ChatTargetVO toVO(ChatTarget target) {
        return ChatTargetVO.builder()
                .id(target.getId())
                .type(target.getType().name())
                .name(target.getName())
                .description(target.getDescription())
                .status(target.getStatus())
                .unavailableReason(target.getUnavailableReason())
                .build();
    }

    private ChatTargetType parseType(String value) {
        return ChatTargetType.valueOf(value.toUpperCase());
    }

}
