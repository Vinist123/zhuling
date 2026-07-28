package com.vinist.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI 兼容接口请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiApiRequestDTO {

    /** 模型名称 */
    private String model;

    /** 消息列表 */
    private Object messages;

    /** 是否流式 */
    @Builder.Default
    private Boolean stream = false;

    /** 其他扩展参数 */
    private Object extra;

}
