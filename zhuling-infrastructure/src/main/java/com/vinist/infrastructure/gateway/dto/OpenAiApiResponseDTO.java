package com.vinist.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 兼容接口响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiApiResponseDTO {

    /** 响应 ID */
    private String id;

    /** 对象类型 */
    private String object;

    /** 创建时间 */
    private Long created;

    /** 模型名称 */
    private String model;

    /**  choices */
    private List<Choice> choices;

    /** usage */
    private Usage usage;

    /**
     * Choice
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Integer index;
        private Message message;
        private Delta delta;
        private String finishReason;
    }

    /**
     * Message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    /**
     * Delta (streaming)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        private String role;
        private String content;
    }

    /**
     * Usage
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

}
