package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.workflow.WorkflowRuntime;

import java.util.List;

/**
 * Workflow 配置/运行时服务。
 *
 * <p>负责 workflows/*.yml 的加载、校验、编译与引用解析，
 * 产出可调度的 {@link WorkflowRuntime}。</p>
 */
public interface IWorkflowConfigService {

    /** 获取编译成功的 Workflow 运行时；不存在时抛出 IllegalArgumentException */
    WorkflowRuntime getRequired(String workflowId);

    /** 全部编译成功的 Workflow 运行时（按 id 排序） */
    List<WorkflowRuntime> getAllWorkflowRuntimes();

}
