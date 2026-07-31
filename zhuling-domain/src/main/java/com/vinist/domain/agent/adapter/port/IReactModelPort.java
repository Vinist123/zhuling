package com.vinist.domain.agent.adapter.port;

import com.vinist.domain.agent.model.react.ReactExecutionContext;
import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactModelResponse;
import reactor.core.publisher.Mono;

/**
 * ReAct 模型交互端口。
 *
 * <p>基础设施层负责将具体模型响应转换为结构化响应，并根据已完成步骤回放
 * assistant tool calls 与 tool responses。</p>
 */
public interface IReactModelPort {

    Mono<ReactModelResponse> call(ReactExecutionRequest request, ReactExecutionContext context);

}
