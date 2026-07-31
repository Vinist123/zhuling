package com.vinist.domain.agent.model.react;

import com.vinist.domain.agent.model.telemetry.ChatUsageMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactModelResponse {

    private String content;

    private String reasoning;

    @Builder.Default
    private List<ReactToolCall> toolCalls = List.of();

    /** 单次模型调用的 token 用量（可能为 null，取决于模型是否返回 usage） */
    private ChatUsageMetrics usage;

}
