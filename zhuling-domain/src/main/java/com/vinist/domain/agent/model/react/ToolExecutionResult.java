package com.vinist.domain.agent.model.react;

/**
 * 工具执行结构化结果（含遥测元数据）。
 *
 * <p>供 ReAct 执行器在 executeTool 时使用，避免依赖 ThreadLocal 跨线程采集。
 *
 * @param output     工具输出（成功时非空，失败时可能为 null）
 * @param durationMs 执行耗时（毫秒）
 * @param serverName 工具所属服务器名（如 MCP server name、local、skill）
 * @param toolType   工具类型（如 function、local、mcp、skill）
 * @param success    是否执行成功
 * @param errorMessage 失败时的错误消息（成功时为 null）
 */
public record ToolExecutionResult(
        String output,
        long durationMs,
        String serverName,
        String toolType,
        boolean success,
        String errorMessage
) {
}
