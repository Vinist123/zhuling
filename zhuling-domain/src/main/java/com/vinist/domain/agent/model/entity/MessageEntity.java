package com.vinist.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEntity {

    /** 主键ID */
    private String id;

    /** 关联的会话ID */
    private String sessionId;

    /** 角色：user/assistant/system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 元数据(工具调用、可观测性等) */
    private Map<String, Object> metadata;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
