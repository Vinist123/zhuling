package com.vinist.domain.agent.adapter.port;

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

}
