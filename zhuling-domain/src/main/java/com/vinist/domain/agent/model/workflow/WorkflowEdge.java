package com.vinist.domain.agent.model.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Workflow 边。
 *
 * <p>非 CONDITION 节点出边必须全部无条件（可多条，即<strong>并行扇出</strong>，调度期并发执行各目标）；
 * CONDITION 节点出边必须全部携带 {@code when} 条件键，与条件组件
 * {@link IWorkflowCondition#route} 返回值精确匹配。一个节点若被 ≥2 条无条件边指向，
 * 则作为<strong>汇聚（Join）节点</strong>，运行时以屏障等待所有并行分支到达后执行一次。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {

    /** 起点节点 id */
    private String fromNodeId;

    /** 终点节点 id */
    private String toNodeId;

    /**
     * 条件键（仅 CONDITION 节点的出边允许）：
     * 条件组件 route() 返回该值时选中本边。
     */
    private String when;

    public boolean isConditional() {
        return when != null && !when.isBlank();
    }

}
