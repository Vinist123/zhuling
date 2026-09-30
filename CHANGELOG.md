# Changelog

本项目所有重要变更记录于此文件。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 进行中

- Phase 8 多 Agent 协同（规划中）

## [Phase 7.5] - 2026-08-14

> ReAct 推理过程从「步骤级实时」升级为「逐 token 增量流式」，观感对齐原生流式路径。

### Added

- **流式模型 chunk 模型 `ReactModelChunk`**：新增 ReAct 模型流式产出的单 chunk 表示，含累积式 `reasoning` 快照、增量 `contentDelta`、合并后的 `toolCalls`、`usage`；内置 `aggregate(List)` 将 chunk 列表聚合为完整 `ReactModelResponse`。
- **`IReactModelPort.stream()` 流式契约**：端口新增 `Flux<ReactModelChunk> stream(...)` 为核心方法；`call()` 降级为默认实现（聚合 stream 结果），同步路径零改动复用。

### Changed

- **`SpringAiNativeReactModelPort`**：`chatModel.call()`（同步整段）改为 `chatModel.stream()`（逐 chunk），每个 `ChatResponse` 转 `ReactModelChunk`；移除冗余的 `toResponse()`，`extractReasoning` / `extractUsage` 复用。
- **`ReactExecutor.executeNext`**：模型调用从 `call().map()` 改为 `stream().doOnNext(透传).collectList().map(aggregate)`——逐 chunk 调 `publishReasoningDelta`（累积快照，内部自动算增量）实现逐 token 实时推送，末尾聚合后走原有循环决策（工具判断 / 结果回灌 / 预算控制不变）。
- **`publishStepCompleted` 兜底**：原「整段补发 reasoning」改为去重安全网（流式已逐 token 推过，`reasoningSnapshot` 去重为 no-op），并补齐 `reasoningContentEnabled` 开关检查。

### Resolved

- ~~**ReAct 模式流式观感为「步骤级实时」而非「逐 token 增量」**~~：已通过 `IReactModelPort` 契约从 `Mono` 升级为 `Flux` 解决。增量展示与循环决策解耦——`tool_calls` 在响应末尾，循环本就需等完整响应才决策（不变）；增量只优化推理过程的实时透传，不动控制流。SSE 事件信封语义不变，前端 `reasoning.delta` 处理逻辑无需改动。

## [Phase 7] - 2026-07-31

> 单 Agent ReAct 执行内核正式落地，可观测对话工作台支持实时步骤轨迹。

### Added

- **框架无关的执行引擎**：新增 `IAgentExecutionEngine` 接口与统一 `React*` 结果模型（`ReactStep` / `ReactExecutionContext` / `ReactExitReason` / `ReactExecutionResult` 等），不绑定任何第三方 Agent 框架。
- **顺序 ReAct 执行器 `ReactExecutor`**：基于 SpringAI 原生 `tool_calls` 实现 `thought → act → observe` 循环；支持工具调用判断（无 tool_call 即结束）、工具结果回灌（保留 tool call ID 关联）、推理-行动预算控制（`maxSteps` / `maxToolCalls` / LLM 超时）。
- **过程级观测**：每轮对话的 ReAct 过程（退出原因 `exitReason`、步数、工具总数、单步摘要、时序）写入消息 `metadata.react` 段，刷新后可回放。
- **步骤级 SSE 事件**：在既有事件信封上新增 `agent.step.started` / `agent.step.completed` / `agent.loop.completed` 三个非终态事件；并复用 `reasoning.delta` / `tool.started` / `tool.completed` 实时推送多步轨迹。
- **节点生命周期拦截器**：接入责任链 `BusinessLinkedList`，内置 `ObservationReactNodeInterceptor`（观测）与 `ErrorNormalizeReactNodeInterceptor`（错误归一化，分类 `MODEL_ERROR` / `TOOL_ERROR` / `TIMEOUT` / `BUDGET_EXHAUSTED` / `POLICY_DENIED` / `INTERNAL`），并预留安全策略扩展点。
- **前端实时步骤轨迹**（`zhuling-ui/index.html`）：每个 ReAct Step 以独立分组容器展示「推理 → 工具 → 结果」真实顺序；气泡摘要标注 `ReAct · N 步`；右侧观测面板新增「ReAct 过程」段。
- **双路径统一**：同步 `POST /api/v1/chat/sync` 与流式 `POST /api/v1/chat/stream` 均走完整 ReAct 循环。

### Changed

- `ChatServiceImpl`：`streamChat()` 在 `react-enabled` 开启时路由到 `emitReactStream()`（响应式订阅执行，步骤级事件实时推送，而非阻塞跑完再回放）。
- `ToolExecutorPortImpl`：新增 `executeWithMetadata()`，直接提取工具 `serverName` / `toolType` 并计时，补全 `toolCalls` 遥测的 `durationMs` / `serverName` / `toolType` 字段（不再依赖跨线程 ThreadLocal）。
- Agent YAML `module.observability.react-enabled` 开关正式生效；`module.react` 参数（`max-steps` / `max-tool-calls` / `llm-timeout-ms`）由 Phase 7 执行内核读取。

### Known Issues

- ~~**ReAct 模式流式观感为「步骤级实时」而非「逐 token 增量」**~~：已于 Phase 7.5（2026-08-14）解决，见上文 `[Phase 7.5]` 段。

## [Phase 6.6] - 2026-07 (基线)

> 多 Agent 运行时基础：独立运行时隔离、对话目标选择、可观测工作台、版本化 SSE 事件信封。

