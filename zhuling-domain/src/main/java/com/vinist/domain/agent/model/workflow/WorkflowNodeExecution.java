package com.vinist.domain.agent.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个 Workflow 节点的执行记录（观测/持久化数据源，批次三消费）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeExecution {

    /** 执行序号（从 1 开始） */
    private int index;

    private String nodeId;

    private String nodeName;

    private WorkflowNodeType type;

    /** 节点输出（CONDITION 节点为命中的分支键） */
    private Object output;

    /** 路由到的下一节点 id；null 表示终止节点 */
    private String routedToNodeId;

    /** 并行扇出时的多个下一节点 id（顺序分支时为空或仅含 routedToNodeId） */
    private List<String> routedToNodeIds;

    /** 耗时毫秒 */
    private long durationMs;

    /** 失败原因；成功时为 null */
    private String error;

    public boolean isSuccess() {
        return error == null;
    }

}
