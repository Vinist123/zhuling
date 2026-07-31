package com.vinist.domain.agent.adapter.port;

import com.vinist.domain.agent.model.react.ToolExecutionResult;

/**
 * 工具执行端口接口
 *
 * <p>负责根据工具名称定位工具并执行，供应用层或后续 ReAct 主循环复用。
 */
public interface IToolExecutorPort {

    /**
     * 执行指定工具
     *
     * @param targetId Agent ID
     * @param toolName 工具名称
     * @param toolInput JSON 字符串格式的工具输入参数
     * @return 工具执行结果
     */
    String execute(String targetId, String toolName, String toolInput);

    /**
     * 执行指定工具并返回结构化结果（含遥测元数据：durationMs/serverName/toolType）。
     *
     * <p>供 ReAct 执行器使用，避免依赖 ThreadLocal 跨线程采集。
     *
     * @param targetId Agent ID
     * @param toolName 工具名称
     * @param toolInput JSON 字符串格式的工具输入参数
     * @return 结构化执行结果（含输出、耗时、工具元数据、成功/失败状态）
     */
    ToolExecutionResult executeWithMetadata(String targetId, String toolName, String toolInput);

}
