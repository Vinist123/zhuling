package com.vinist.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话创建请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequestDTO {

    /** 对话目标 ID */
    private String targetId;

    /** 对话目标类型：AGENT/WORKFLOW */
    private String targetType;

    /** 用户 ID */
    private String userId;

    /** 会话标题 */
    private String title;

}
