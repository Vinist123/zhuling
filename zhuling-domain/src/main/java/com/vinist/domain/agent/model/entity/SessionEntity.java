package com.vinist.domain.agent.model.entity;

import com.vinist.domain.agent.model.ChatTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity {

    /** 主键ID */
    private String id;

    /** 对话目标 ID */
    private String targetId;

    /** 对话目标类型 */
    private ChatTargetType targetType;

    /** 用户ID */
    private String userId;

    /** 会话标题 */
    private String title;

    /** 状态：active/closed */
    private String status;

    /** 消息数量 */
    private Integer messageCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
