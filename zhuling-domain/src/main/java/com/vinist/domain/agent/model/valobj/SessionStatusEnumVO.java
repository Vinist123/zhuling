package com.vinist.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话状态值对象
 */
@Getter
@AllArgsConstructor
public enum SessionStatusEnumVO {

    ACTIVE("active", "活跃"),
    CLOSED("closed", "已关闭");

    private final String code;
    private final String desc;

}
