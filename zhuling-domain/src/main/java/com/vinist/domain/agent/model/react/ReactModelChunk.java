package com.vinist.domain.agent.model.react;

import com.vinist.domain.agent.model.telemetry.ChatUsageMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ReAct 模型流式产出的单个 chunk。
 *
 * <p>SpringAI 2.0.x 流式模式下的字段语义（经源码确认）：
 * <ul>
 *   <li><b>content</b>：增量片段，每个 chunk 是一小段文本，需拼接成完整内容</li>
 *   <li><b>reasoning</b>：累积式快照，每个 chunk 携带到目前为止的完整思维链，取最后一个非空即可
 *      （{@code OpenAiChatModel} 内部以 {@code String::concat} 累积 reasoning）</li>
 *   <li><b>toolCalls</b>：SpringAI 用 {@code bufferUntil + mergeChunks} 将工具调用相关 chunks
 *       合并为一个完整列表（按 index 合并 arguments），因此流中通常仅一个 chunk 携带完整 toolCalls</li>
 *   <li><b>usage</b>：累积式，通常仅末尾 chunk 携带</li>
 * </ul>
 *
 * <p>这些语义使得 {@link #aggregate(List)} 的聚合逻辑极简且低风险：
 * content 拼接、reasoning/toolCalls/usage 各取最后一个非空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactModelChunk {

    /** 累积式 reasoning 快照（可为 null） */
    private String reasoning;

    /** 内容增量片段（可为 null/空） */
    private String contentDelta;

    /** 工具调用列表（SpringAI 已合并为完整列表，通常仅一个 chunk 携带；可为空） */
    @Builder.Default
    private List<ReactToolCall> toolCalls = List.of();

    /** token 用量（累积式，通常仅末尾 chunk 携带；可为 null） */
    private ChatUsageMetrics usage;

    /**
     * 将流式 chunk 列表聚合为完整响应。
     *
     * <p>聚合规则：
     * <ul>
     *   <li>content：拼接所有 contentDelta（增量式）</li>
     *   <li>reasoning：取最后一个非空累积值（累积式）</li>
     *   <li>toolCalls：取最后一个非空列表（SpringAI 已合并为完整列表）</li>
     *   <li>usage：取最后一个非空（累积式）</li>
     * </ul>
     *
     * @param chunks 流式 chunk 列表（不可为 null）
     * @return 聚合后的完整响应
     */
    public static ReactModelResponse aggregate(List<ReactModelChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return ReactModelResponse.builder()
                    .content("")
                    .toolCalls(List.of())
                    .build();
        }
        StringBuilder content = new StringBuilder();
        String reasoning = null;
        List<ReactToolCall> toolCalls = List.of();
        ChatUsageMetrics usage = null;
        for (ReactModelChunk chunk : chunks) {
            if (chunk.getContentDelta() != null) {
                content.append(chunk.getContentDelta());
            }
            if (chunk.getReasoning() != null && !chunk.getReasoning().isBlank()) {
                reasoning = chunk.getReasoning();
            }
            if (chunk.getUsage() != null) {
                usage = chunk.getUsage();
            }
            // SpringAI 已将工具调用 chunks 合并为完整列表，取最后一个非空
            if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                toolCalls = chunk.getToolCalls();
            }
        }
        return ReactModelResponse.builder()
                .content(content.toString())
                .reasoning(reasoning)
                .toolCalls(toolCalls)
                .usage(usage)
                .build();
    }

}
