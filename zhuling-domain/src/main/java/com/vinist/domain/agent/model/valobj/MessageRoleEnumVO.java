package com.vinist.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息角色值对象
 */
@Getter
@AllArgsConstructor
public enum MessageRoleEnumVO {

    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    SYSTEM("system", "系统");

    private final String code;
    private final String desc;

}
