package com.vinist.infrastructure.runtime.react;

import com.vinist.domain.agent.adapter.port.IReactModelPort;
import com.vinist.domain.agent.model.entity.MessageEntity;
import com.vinist.domain.agent.model.react.ReactExecutionContext;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactModelResponse;
import com.vinist.domain.agent.model.react.ReactStep;
import com.vinist.domain.agent.model.react.ReactToolCall;
import com.vinist.domain.agent.model.react.ReactToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 ChatModel 原生调用的 ReAct 模型端口。
 *
 * <p>不使用 ChatClient 工具 Advisor，确保模型返回 tool_calls 后由脚手架执行循环。</p>
 */
public class SpringAiNativeReactModelPort implements IReactModelPort {

    private static final List<String> REASONING_KEYS = List.of(
            "reasoningContent", "reasoning_content", "reasoning", "thinking", "thought");

    private final ChatModel chatModel;

    public SpringAiNativeReactModelPort(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Mono<ReactModelResponse> call(ReactExecutionRequest request, ReactExecutionContext context) {
        return Mono.fromCallable(() -> {
                    ChatResponse response = chatModel.call(new Prompt(buildMessages(request, context)));
                    return toResponse(response);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<Message> buildMessages(ReactExecutionRequest request, ReactExecutionContext context) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getHistory() != null) {
            for (MessageEntity historyMessage : request.getHistory()) {
                appendHistoryMessage(messages, historyMessage);
            }
        }
        messages.add(new UserMessage(request.getUserMessage()));

        if (context.getCompletedSteps() != null) {
            for (ReactStep step : context.getCompletedSteps()) {
                appendCompletedStep(messages, step);
            }
        }
        return messages;
    }

    private void appendHistoryMessage(List<Message> messages, MessageEntity historyMessage) {
        if (historyMessage.getContent() == null) {
            return;
        }
        switch (historyMessage.getRole()) {
            case "user" -> messages.add(new UserMessage(historyMessage.getContent()));
            case "assistant" -> messages.add(new AssistantMessage(historyMessage.getContent()));
            default -> {
            }
        }
    }

    private void appendCompletedStep(List<Message> messages, ReactStep step) {
        ReactModelResponse response = step.getModelResponse();
        if (response == null) {
            return;
        }
        List<AssistantMessage.ToolCall> toolCalls = response.getToolCalls().stream()
                .map(toolCall -> new AssistantMessage.ToolCall(
                        toolCall.getId(), "function", toolCall.getName(), toolCall.getArguments()))
                .toList();
        messages.add(AssistantMessage.builder()
                .content(response.getContent())
                .toolCalls(toolCalls)
                .build());

        if (step.getToolResults() == null || step.getToolResults().isEmpty()) {
            return;
        }
        List<ToolResponseMessage.ToolResponse> responses = step.getToolResults().stream()
                .map(this::toToolResponse)
                .toList();
        messages.add(ToolResponseMessage.builder().responses(responses).build());
    }

    private ToolResponseMessage.ToolResponse toToolResponse(ReactToolResult result) {
        return new ToolResponseMessage.ToolResponse(result.getId(), result.getName(), result.getOutput());
    }

    private ReactModelResponse toResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回有效响应");
        }
        AssistantMessage output = response.getResult().getOutput();
        return ReactModelResponse.builder()
                .content(output.getText())
                .reasoning(extractReasoning(output.getMetadata()))
                .toolCalls(output.getToolCalls().stream()
                        .map(toolCall -> ReactToolCall.builder()
                                .id(toolCall.id())
                                .name(toolCall.name())
                                .arguments(toolCall.arguments())
                                .build())
                        .toList())
                .usage(extractUsage(response))
                .build();
    }

    private com.vinist.domain.agent.model.telemetry.ChatUsageMetrics extractUsage(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
        return com.vinist.domain.agent.model.telemetry.ChatUsageMetrics.builder()
                .promptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null)
                .completionTokens(usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null)
                .totalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null)
                .build();
    }

    private String extractReasoning(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : REASONING_KEYS) {
            Object value = metadata.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

}
