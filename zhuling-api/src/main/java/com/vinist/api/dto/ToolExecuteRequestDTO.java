package com.vinist.api.dto;

import lombok.Data;

/**
 * 工具执行请求
 */
@Data
public class ToolExecuteRequestDTO {

    /** Agent ID */
    private String targetId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * JSON 字符串格式的工具输入
     */
    private String toolInput;

}
