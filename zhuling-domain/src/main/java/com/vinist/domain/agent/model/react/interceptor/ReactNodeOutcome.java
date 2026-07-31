package com.vinist.domain.agent.model.react.interceptor;

import com.vinist.domain.agent.model.react.ReactModelResponse;
import com.vinist.domain.agent.model.react.ReactToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ReAct 节点执行结果载体。
 *
 * <p>由节点（MODEL / TOOL）执行完成并经拦截器链 {@code afterNode} 处理后得到，
 * 携带成功标志与对应类型的产出；失败时 {@code error} 非空。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactNodeOutcome {

    /** 节点类型 */
    private ReactNodeContext.NodeType nodeType;

    /** 步骤序号 */
    private int stepIndex;

    /** 是否执行成功（节点未抛异常即视为成功；工具业务失败仍算成功，由 toolResults 标记） */
    private boolean success;

    /** MODEL 节点成功时非空 */
    private ReactModelResponse modelResponse;

    /** TOOL 节点成功时非空 */
    private List<ReactToolResult> toolResults;

    /** 失败时的归一化异常（成功时为 null） */
    private ReactNodeException error;

}
