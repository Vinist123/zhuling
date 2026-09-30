package com.vinist.domain.agent.adapter.port;

import com.vinist.domain.agent.model.react.ReactExecutionContext;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactModelChunk;
import com.vinist.domain.agent.model.react.ReactModelResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReAct 模型交互端口。
 *
 * <p>基础设施层负责将具体模型响应转换为结构化响应，并根据已完成步骤回放
 * assistant tool calls 与 tool responses。</p>
 *
 * <p>流式优先设计：{@link #stream} 为核心方法，逐 chunk 返回模型产出；
 * {@link #call} 为默认实现，聚合流式结果后一次性返回，供同步路径复用。</p>
 */
public interface IReactModelPort {

    /**
     * 流式调用模型：逐 chunk 返回，用于 ReAct 循环中推理过程的实时透传。
     *
     * <p>SpringAI 2.0.x 下：reasoning 为累积式快照、content 为增量片段、
     * toolCalls 由框架合并为完整列表（通常仅一个 chunk 携带）。</p>
     *
     * @param request 执行请求
     * @param context 执行上下文（含已完成步骤，用于回放 assistant tool calls）
     * @return 流式 chunk 序列
     */
    Flux<ReactModelChunk> stream(ReactExecutionRequest request, ReactExecutionContext context);

    /**
     * 同步调用模型：聚合流式结果后一次性返回完整响应。
     *
     * <p>默认实现基于 {@link #stream} + {@link ReactModelChunk#aggregate}，
     * 子类通常无需覆盖。</p>
     */
    default Mono<ReactModelResponse> call(ReactExecutionRequest request, ReactExecutionContext context) {
        return stream(request, context).collectList().map(ReactModelChunk::aggregate);
    }

}
