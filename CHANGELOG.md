# Changelog

本项目所有重要变更记录于此文件。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 进行中

- Phase 8 多 Agent 协同（规划中）

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

- **ReAct 模式流式观感为「步骤级实时」而非「逐 token 增量」**：开启 `react-enabled` 后，`IReactModelPort.call()` 以 `Mono<ReactModelResponse>` 一次性返回整段推理，前端在每个 Step 结束时整段补发 `reasoning.delta`，观感上比关闭 ReAct（走 `ChatModelPort.stream()` 原生逐字流）显得「卡顿、无流式」。该问题仅影响观感、不改变 ReAct 循环速度，已记录于 `docs/DEVELOPMENT_PLAN.md` Phase 7「后续优化」项，建议作为独立 Enhancement 排期（将 `call()` 升级为 `Flux` 增量推送）。

## [Phase 6.6] - 2026-07 (基线)

> 多 Agent 运行时基础：独立运行时隔离、对话目标选择、可观测工作台、版本化 SSE 事件信封。

