package com.vinist.domain.agent.model.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用遥测
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallTelemetry {

    private String toolName;

    private String toolType;

    private String serverName;

    private String inputPreview;

    private String outputPreview;

    private Long durationMs;

    private Boolean success;

    private String errorType;

    private String errorMessage;

}
