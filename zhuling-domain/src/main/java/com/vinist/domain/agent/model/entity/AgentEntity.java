package com.vinist.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEntity {

    /** 主键ID */
    private String id;

    /** Agent名称 */
    private String name;

    /** Agent描述 */
    private String description;

    /** Agent配置(JSON) */
    private String config;

    /** 状态：active/inactive */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
