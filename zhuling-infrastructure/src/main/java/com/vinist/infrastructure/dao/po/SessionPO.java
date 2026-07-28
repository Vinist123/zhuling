package com.vinist.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_session")
public class SessionPO {

    /** 主键ID */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 对话目标 ID */
    @TableField("target_id")
    private String targetId;

    /** 对话目标类型 */
    @TableField("target_type")
    private String targetType;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 会话标题 */
    @TableField("title")
    private String title;

    /** 状态：active/closed */
    @TableField("status")
    private String status;

    /** 消息数量 */
    @TableField("message_count")
    private Integer messageCount;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
