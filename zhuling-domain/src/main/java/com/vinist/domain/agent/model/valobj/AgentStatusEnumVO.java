package com.vinist.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent状态值对象
 */
@Getter
@AllArgsConstructor
public enum AgentStatusEnumVO {

    ACTIVE("active", "活跃"),
    INACTIVE("inactive", "非活跃");

    private final String code;
    private final String desc;

}
