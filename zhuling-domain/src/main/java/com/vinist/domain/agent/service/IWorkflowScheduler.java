package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.telemetry.TurnTelemetry;
import com.vinist.domain.agent.model.workflow.WorkflowExecutionResult;
import com.vinist.domain.agent.model.workflow.WorkflowRuntime;
import reactor.core.publisher.Mono;

/**
 * Workflow 调度器：按编译后的受控 DAG 顺序执行节点。
 *
 * <p>AGENT 节点委托 {@code IAgentExecutionEngine}（ReAct 内核），
 * CODE 节点执行显式注册组件，CONDITION 节点路由跳转。
 * 用户消息以 {@code userMessage} 变量进入黑板（无 input-template 的
 * AGENT 节点直接以用户消息为输入）。</p>
 */
public interface IWorkflowScheduler {

    /**
     * 执行一个 Workflow（无遥测：不发 SSE 事件、不写 telemetry）。
     */
    default Mono<WorkflowExecutionResult> execute(WorkflowRuntime runtime, String userMessage) {
        return execute(runtime, userMessage, null);
    }

    /**
     * 执行一个 Workflow（带本轮遥测上下文）。
     *
     * <p>telemetry 非空时：发布 {@code workflow.node.started/.completed} 与
     * {@code workflow.completed} SSE 事件；内部 AGENT 节点透传 telemetry
     * （reasoning.delta / agent.step.* 实时事件 + usage 累计）；
     * 结束后写入 telemetry.workflowProcess（供 metadata 落库）。</p>
     *
     * @param runtime     编译完成的 Workflow 运行时
     * @param userMessage 用户消息（可空；进入黑板 variables.userMessage）
     * @param telemetry   本轮遥测上下文（可空）
     * @return 执行结果（永远非空；节点失败不抛异常，以 exitReason 表达）
     */
    Mono<WorkflowExecutionResult> execute(WorkflowRuntime runtime, String userMessage, TurnTelemetry telemetry);

}
