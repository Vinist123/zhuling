package com.vinist.infrastructure.adapter.port;

import com.vinist.domain.agent.adapter.port.IChatModelPort;
import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.telemetry.ReasoningContentStatus;
import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.service.IConversationStreamEventPublisher;
import com.vinist.domain.agent.service.ITurnTelemetryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用端口实现
 *
 * <p>基于 ChatClient 封装，支持系统提示词和历史消息上下文
 */
@Slf4j
public class ChatModelPortImpl implements IChatModelPort {

    private final ChatClient chatClient;
    private final ITurnTelemetryService turnTelemetryService;
    private final IConversationStreamEventPublisher eventPublisher;

    public ChatModelPortImpl(ChatClient chatClient,
                             ITurnTelemetryService turnTelemetryService,
                             IConversationStreamEventPublisher eventPublisher) {
        this.chatClient = chatClient;
        this.turnTelemetryService = turnTelemetryService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String call(String systemPrompt, List<MessageEntity> history, String userMessage) {
        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        ChatResponse response = chatClient.prompt()
                .messages(messages)
                .call()
                .chatResponse();

        // 同步模式：ThreadLocal 在同线程，currentTurn() 可正常获取
        TurnTelemetry telemetry = turnTelemetryService.currentTurn();
        captureUsage(response, telemetry);
        captureReasoning(response, telemetry);
        return extractContent(response);
    }

    @Override
    public Flux<String> stream(String systemPrompt, List<MessageEntity> history, String userMessage, TurnTelemetry telemetry) {
        List<Message> messages = buildMessages(systemPrompt, history, userMessage);

        // 流式模式：通过参数传递 telemetry 引用，captureUsage/captureReasoning 不依赖 ThreadLocal
        // 同时用 Flux.defer 在订阅线程绑定 ThreadLocal，让 TelemetryToolCallback 能通过 currentTurn() 获取
        // 原因：Reactor 的 boundedElastic/netty 线程不传播 ThreadLocal，
        //       工具回调由 SpringAI 内部触发，无法通过参数传递 telemetry，只能依赖 ThreadLocal
        return Flux.defer(() -> {
            if (telemetry != null) {
                turnTelemetryService.bindCurrentTurn(telemetry);
            }
            return chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> captureUsage(response, telemetry))
                    .doOnNext(response -> captureReasoning(response, telemetry))
                    .map(this::extractContent)
                    .filter(content -> content != null && !content.isEmpty())
                    .doFinally(signalType -> turnTelemetryService.clearCurrentTurn());
        });
    }

    /**
     * 构建 Spring AI 消息列表
     *
     * <p>消息顺序：SystemPrompt（若有）→ 历史消息 → 当前用户消息
     */
    private List<Message> buildMessages(String systemPrompt, List<MessageEntity> history, String userMessage) {
        List<Message> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }

        if (history != null) {
            for (MessageEntity entity : history) {
                String role = entity.getRole();
                String content = entity.getContent();
                if (content == null) {
                    continue;
                }
                switch (role) {
                    case "user" -> messages.add(new UserMessage(content));
                    case "assistant" -> messages.add(new AssistantMessage(content));
                    default -> {
                        // 其他角色（如 system）跳过
                    }
                }
            }
        }

        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private void captureUsage(ChatResponse response, TurnTelemetry telemetry) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        if (telemetry == null) {
            return;
        }

        var usage = response.getMetadata().getUsage();
        turnTelemetryService.recordUsage(
                telemetry,
                usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null,
                usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null,
                usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null);
    }

    /**
     * 采集 reasoning（思维链）信息，并根据可获取性判定三态状态。
     *
     * <p>判定逻辑：
     * <ul>
     *   <li><b>STABLE（可稳定获取）</b> — 从 ChatResponse 输出 metadata 中捕获到非空白 reasoning/thinking 字段，
     *       且已写入 TurnTelemetry.reasoningContent。</li>
     *   <li><b>UNSTABLE（不稳定）</b> — 模型可能支持 reasoning，但本次响应中未捕获到有效内容。
     *       可能原因：非推理模型、thinking 开关关闭、框架版本不支持等。</li>
     *   <li><b>UNSUPPORTED（不支持）</b> — 初始状态，表示尚未完成判定；若整轮结束仍为此状态，
     *       则说明该模型/框架组合明确不支持或无法获取 reasoning_content。</li>
     * </ul>
     *
     * <p>SpringAI 2.0.x 流式模式下，reasoningContent 是<b>累积式</b>返回的——
     * 每个 chunk 的 metadata.reasoningContent 包含到目前为止的完整思维链内容，而非增量片段。
     * 因此每次直接 set 即可，最后一次 set 就是完整的思维链。
     *
     * <p>关键修复点：
     * <ul>
     *   <li>移除"已有值则跳过"的提前 return（会导致只保留首个片段）</li>
     *   <li>content chunk（reasoningContent 为空）不覆盖已有状态</li>
     * </ul>
     */
    private void captureReasoning(ChatResponse response, TurnTelemetry telemetry) {
//        log.info("captureReasoning: {}", response.getResult().getOutput().getMetadata());
        if (telemetry == null) {
            return;
        }

        String reasoningContent = extractReasoningContent(response);

        if (reasoningContent != null && !reasoningContent.isBlank()) {
            // STABLE：已从 ChatResponse 输出 metadata 中捕获 reasoning/thinking 字段
            turnTelemetryService.recordReasoningContent(telemetry, reasoningContent);
            turnTelemetryService.recordReasoningCapability(
                    telemetry,
                    ReasoningContentStatus.STABLE,
                    "已从 ChatResponse 输出 metadata 中捕获 reasoning/thinking 字段");
            // 仅当开关未关闭时发布 reasoning SSE 事件
            if (!Boolean.FALSE.equals(telemetry.getReasoningContentEnabled())) {
                eventPublisher.publishReasoningDelta(telemetry, reasoningContent);
            }
            log.debug("captureReasoning: STABLE - 已采集思维链, length={}", reasoningContent.length());
            return;
        }

        // UNSTABLE：content chunk / usage chunk 到达时 reasoningContent 为空
        // 不覆盖已有状态，仅记录一次"不稳定"提示
        if (telemetry.getReasoningStatus() == ReasoningContentStatus.UNSUPPORTED) {
            turnTelemetryService.recordReasoningCapability(
                    telemetry,
                    ReasoningContentStatus.UNSTABLE,
                    "当前 chunk 未捕获 reasoning_content；模型可能支持但未返回，或本 chunk 为 content/usage 类型");
        }
    }

    private String extractReasoningContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }

        Map<String, Object> metadata = response.getResult().getOutput().getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        // SpringAI 2.0.x 使用 camelCase 的 reasoningContent
        List<String> candidateKeys = List.of(
                "reasoningContent",
                "reasoning_content",
                "reasoning",
                "thinking",
                "thought"
        );

        for (String key : candidateKeys) {
            Object value = metadata.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        return null;
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String content = response.getResult().getOutput().getText();
        return content != null ? content : "";
    }

}
