package com.vinist.domain.agent.model.react.interceptor;

/**
 * ReAct 节点异常归一化分类。
 *
 * <p>拦截器链的 {@code onFailure}/{@code normalize} 将原始异常映射到稳定的分类，
 * 供执行器统一转换为 {@code ReactExitReason} 或安全策略判断。</p>
 */
public enum ReactNodeErrorCategory {

    /** 模型调用失败（含推理、协议、反序列化等） */
    MODEL_ERROR,

    /** 工具执行失败（本地工具、MCP、Skill 等） */
    TOOL_ERROR,

    /** 单次模型调用超时 */
    TIMEOUT,

    /** 预算耗尽（最大步数 / 最大工具调用次数） */
    BUDGET_EXHAUSTED,

    /** 安全策略拒绝（预留：输入/输出安全预检不通过） */
    POLICY_DENIED,

    /** 内部未归类异常 */
    INTERNAL

}
