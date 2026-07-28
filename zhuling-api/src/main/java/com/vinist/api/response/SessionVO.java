package com.vinist.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 会话信息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO {

    /** 会话 ID */
    private String id;

    /** 对话目标 ID */
    private String targetId;

    /** 对话目标类型：AGENT/WORKFLOW */
    private String targetType;

    /** 用户 ID */
    private String userId;

    /** 会话标题 */
    private String title;

    /** 状态 */
    private String status;

    /** 消息数量 */
    private Integer messageCount;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 最近一条消息 */
    private MessageSummaryVO lastMessage;

    /**
     * 消息摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageSummaryVO {
        private String id;
        private String role;
        private String content;
        private Map<String, Object> metadata;
    }

}
