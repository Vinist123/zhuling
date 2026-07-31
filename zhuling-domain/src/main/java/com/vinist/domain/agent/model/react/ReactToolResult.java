package com.vinist.domain.agent.model.react;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactToolResult {

    private String id;

    private String name;

    private String arguments;

    private String output;

    private boolean failed;

    /** 工具执行耗时（毫秒） */
    private Long durationMs;

    /** 工具所属服务器名（MCP server name / local / skill） */
    private String serverName;

    /** 工具类型（function / local / mcp / skill） */
    private String toolType;

}
