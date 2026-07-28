package com.vinist.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 会话消息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessageVO {

    private String id;

    private String sessionId;

    private String role;

    private String content;

    private Map<String, Object> metadata;

    private String createdAt;

}
