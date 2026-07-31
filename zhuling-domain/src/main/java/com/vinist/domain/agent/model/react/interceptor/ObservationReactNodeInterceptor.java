package com.vinist.domain.agent.model.react.interceptor;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * ReAct 节点观测拦截器。
 *
 * <p>在 {@code beforeNode}/{@code afterNode} 记录节点进入与完成日志（含耗时），
 * 作为脚手架定制观测埋点的基础节点；可在 {@code afterNode} 中扩展结构化指标采集。</p>
 *
 * <p>该拦截器无副作用、不修改结果，仅观测。</p>
 */
@Slf4j
public class ObservationReactNodeInterceptor implements IReactNodeInterceptor {

    @Override
    public Mono<Void> beforeNode(ReactNodeContext context) {
        if (context == null) {
            return Mono.empty();
        }
        log.debug("ReAct 节点进入: type={}, step={}, agentId={}, tool={}",
                context.getNodeType(), context.getStepIndex(), context.getAgentId(),
                context.getToolName());
        return Mono.empty();
    }

    @Override
    public Mono<Void> afterNode(ReactNodeContext context, ReactNodeOutcome outcome) {
        if (context == null || outcome == null) {
            return Mono.empty();
        }
        log.debug("ReAct 节点完成: type={}, step={}, success={}, toolResults={}",
                outcome.getNodeType(), outcome.getStepIndex(), outcome.isSuccess(),
                outcome.getToolResults() != null ? outcome.getToolResults().size() : 0);
        return Mono.empty();
    }

}
