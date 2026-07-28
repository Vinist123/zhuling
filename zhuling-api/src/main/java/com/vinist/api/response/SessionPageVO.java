package com.vinist.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话分页响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPageVO {

    private List<SessionVO> items;

    private int page;

    private int pageSize;

    private long total;

}
