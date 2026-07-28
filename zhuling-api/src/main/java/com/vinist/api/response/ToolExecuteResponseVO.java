package com.vinist.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecuteResponseVO {

    private String toolName;

    private String result;

}
