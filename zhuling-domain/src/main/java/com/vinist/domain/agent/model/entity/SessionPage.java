package com.vinist.domain.agent.model.entity;

import java.util.List;

/**
 * 用户会话分页结果。
 */
public record SessionPage(
        List<SessionEntity> items,
        long total,
        int page,
        int pageSize) {

    public SessionPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

}
