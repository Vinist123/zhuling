package com.vinist.domain.agent.adapter.port;

import com.vinist.domain.agent.model.react.ReactExecutionRequest;
import com.vinist.domain.agent.model.react.ReactExecutionResult;
import reactor.core.publisher.Mono;

/**
 * Agent 执行引擎端口。
 *
 * <p>执行循环和外部编排框架均通过该端口接入，调用方不依赖具体引擎实现。</p>
 */
public interface IAgentExecutionEngine {

    Mono<ReactExecutionResult> execute(ReactExecutionRequest request, IReactModelPort reactModelPort);

}
