package com.vinist.domain.agent.model.react.interceptor;

import com.vinist.domain.agent.model.react.ReactExecutionContext;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct 节点执行上下文。
 *
 * <p>每个执行节点（MODEL 推理节点 / TOOL 工具节点）在执行前后由拦截器链共享，
 * 携带节点类型、步骤序号与可选的工具元信息，以及只读的原始请求与执行上下文。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactNodeContext {

    /** 节点类型：MODEL（推理）或 TOOL（工具） */
    private NodeType nodeType;

    /** 当前步骤序号（从 1 开始） */
    private int stepIndex;

    /** Agent ID */
    private String agentId;

    /** TOOL 节点专用：工具名 */
    private String toolName;

    /** TOOL 节点专用：工具入参原文（JSON） */
    private String toolInput;

    /** 原始执行请求（只读引用） */
    private ReactExecutionRequest request;

    /** 原始执行上下文（只读引用） */
    private ReactExecutionContext executionContext;

    /** 节点类型枚举 */
    public enum NodeType {
        MODEL,
        TOOL
    }

}
