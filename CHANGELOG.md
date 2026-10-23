# Changelog

本项目所有重要变更记录于此文件。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 修复

- **ReAct 跨 step 重复工具调用防护**（2026-10-15）：`ReactExecutor` 新增跨步骤复用——已完成步骤里用相同「工具名+入参」执行过的调用，直接复用上次结果并附加「请勿重复调用，基于已有信息作答」提示，不再重复执行工具。解决真机并行 Workflow 中 query-a 反复查询同一歌手、直到第 4 步 LLM 30s 无响应超时（`LLM_TIMEOUT`）的问题；新增 `ReactExecutorTest` 覆盖复用逻辑（工具只执行 1 次 + 提示回灌）。music-agent `llm-timeout-ms` 30000→60000 增加并行/慢响应余量

### 进行中

- Phase 9 多模态支持（规划中）

## [Phase 8 · 并行分支与 Join] - 2026-10-15

### 新增

- **并行扇出 + Join 汇聚屏障**：受控 DAG 支持「一个节点并行扇出到多个下游」+「多分支汇聚到同一节点」（屏障由最后到达的分支触发执行，避免重复触发）
- **WorkflowCompiler 放开单出边限制**：允许非 CONDITION 节点多条无条件出边（并行扇出）；新增 `validateJoinRules()`——汇聚节点（≥2 条无条件入边）不可同时接收条件分支入边，否则报 `汇聚节点不可同时接收并行分支与条件分支`；非 CONDITION 出边带 `when` 仍报错
- **WorkflowScheduler 并行调度**：`Flux.merge` 并行执行分支，`WorkflowState.arriveJoinBarrier`（`AtomicInteger` 屏障）保证 Join 节点仅执行一次；任一分支失败整体 `NODE_FAILED`（短路，不等待 Join）
- **观测模型扩展**：`WorkflowNodeExecution` / `WorkflowNodeOutcome` / `WorkflowProcessTelemetry.NodeSummary` 增加 `routedToNodeIds`（并行扇出多目标）；`WorkflowEventPublishingInterceptor.afterNode` 实时 payload 同步携带 `routedToNodeIds`
- **前端并行/汇聚可视化**：`index.html` 新增 `computeJoinNodeIds` / `describeWorkflowNode` 辅助函数——节点路由段显示「并行扇出 → a · b」徽标，被 ≥2 分支指向的节点显示「汇聚」徽标（轨迹面板 + metadata inspector 双路径，实时与回放一致）
- **示例并行工作流**：`parallel-music-workflow.yml`（`dispatch → query-a / query-b → combine`）+ 组件 `MusicDispatch`（扇出）、`MusicParallelCombiner`（汇聚，读黑板 queryA/queryB 拼装）
- **单元测试**：`WorkflowSchedulerTest` 新增 6 用例覆盖并行编译/执行/失败/反例与 classpath yml 编译，全部通过（共 12 用例）

### 修改

- 配置加载策略（2026-10-15 确立）：Workflow 启动期仍逐文件完整编译 + 引用校验（"启动时检查声明"），默认 **fail-soft**（单坏 yml 仅 `log.error` + 跳过 + 不进目标列表，不拖垮其他 Agent/Workflow）；`zhuling.workflow.strict=true` 切回 fail-fast 终止启动（与 Agent 侧 UNAVAILABLE 降级一致）

## [Phase 8 · 批次三] - 2026-09-30

### 新增

- **WORKFLOW 对话目标**：`ChatTargetServiceImpl` 合并 Agent + Workflow 目标列表，前端目标选择器直接可见可选；会话创建与消息路由（同步 `chat()` / 流式 `streamChat()` 双路径）支持 `WORKFLOW` 类型
- **SSE 实时节点事件**：`workflow.node.started` / `workflow.node.completed` / `workflow.completed`（新增 `WorkflowEventPublishingInterceptor`，telemetry 非空时叠加进拦截器链）；内部 AGENT 节点透传 telemetry——`reasoning.delta` / `agent.step.*` 实时流式 + token 用量跨节点累计（`UsageAccumulator`）
- **metadata 持久化**：`workflow` 段落库（workflowId/exitReason/节点轨迹/耗时/输出预览，输出截断 200 字符），由 `WorkflowProcessTelemetry.from()` 从执行记录构建，`MessageMetadataCollectorImpl` 采集
- **前端 Workflow 轨迹面板**：`Workflow 模式` 标记 + Node 容器（执行中→完成/失败 + 路由去向 + 输出预览）+ 执行结束汇总行；内部 AGENT 的 ReAct Step 容器按到达顺序交错展示
- **IWorkflowScheduler 契约扩展**：`execute(runtime, message, telemetry)` 带遥测重载（telemetry 可空，空时不发事件不写观测）

### 语义

- Workflow 终态输出降级：`exitReason != COMPLETED` 但存在非空 finalOutput 时降级返回（与 AGENT 路径一致），否则抛 IllegalStateException
- 流式路径终态输出拆 4 字符块回放，复用现有 materialize → handleStreamSignal → onErrorResume 管道

**Phase 8 全部三批次交付完成。**

## [Phase 8 · 批次二] - 2026-09-30

### 新增

- **WorkflowScheduler**（case 层）：顺序 DAG 调度内核。AGENT 节点渲染输入模板后委托 ReAct 引擎单轮执行（跨节点上下文经黑板传递，与会话历史解耦）、CODE 节点执行显式注册组件、CONDITION 节点按组件路由跳转；节点失败不抛异常，以 `NODE_FAILED` 结果返回（与 ChatServiceImpl 同步的优雅降级语义：非 COMPLETED 但有降级回答则接受）
- **节点拦截器链**（domain `model/workflow/interceptor/`）：`WorkflowNodeInterceptorChain` 基于 `BusinessLinkedList` + `LinkArmory` 责任链装配（与 ReactNodeInterceptorChain 同构），内置观测拦截器；before/after/onFailure 生命周期，错误归一化为带节点上下文的异常
- **执行结果模型**：`WorkflowExitReason`（COMPLETED/NODE_FAILED/ROUTE_NOT_MATCHED/MAX_NODES_EXCEEDED）、`WorkflowNodeExecution`（单节点执行记录，观测/持久化数据源）、`WorkflowExecutionResult`（含终态输出与耗时）
- **端到端测试** `WorkflowSchedulerTest`（4 用例全过）：真实 yml 编译 → 调度执行，覆盖 has-result/no-result 双分支、模板渲染（state + 黑板注入）、有环配置拒绝

### 修改

- `zhuling-app/pom.xml`：surefire 从钉死的 2.6（不支持 JUnit 5）改为父 POM 管理版本，移除全局 `skipTests`，改为排除 `ApiTest`（@SpringBootTest 需完整上下文+数据库）

## [Phase 8 · 批次一] - 2026-09-30

### 新增

- **Workflow 领域模型**（`com.vinist.domain.agent.model.workflow`）：`WorkflowDefinition/State/Runtime` 三件套 + `WorkflowNode/Edge/NodeType` + 组件接口 `IWorkflowCodeNode/IWorkflowCondition`。设计决策：简单黑板状态（variables + nodeOutputs 两级 Map，输出优先），复杂逻辑由显式注册的 Java 组件承担
- **WorkflowCompiler**：YAML → 受控 DAG，8 项校验（entry 存在、节点唯一、边引用合法、无自环、普通节点单出边禁 when、CONDITION 出边全带 when 且唯一、Kahn 无环校验、entry 全可达），启动 fail-fast
- **WorkflowNodeRegistryImpl**：按 Spring Bean 名收集 `IWorkflowCodeNode/IWorkflowCondition` 组件，开发者显式注册即可被 YAML 引用
- **WorkflowConfigServiceImpl**：`agent-config/workflows/*.yml` 加载 + 编译 + 引用解析（AGENT 节点解析 AgentRuntime，CODE/CONDITION 解析组件），产出 `WorkflowRuntime` 缓存
- **示例组件**：`musicResultCheckCondition`（条件路由）+ `musicFallbackReplier`（兜底回复）
- **示例 Workflow**：`workflows/music-critic-workflow.yml`（查歌数 → 结果检查 → 点评/兜底，条件路由 + 顺序多 Agent 委派）
- **模板占位符**：`WorkflowState.renderTemplate` 支持 `${key}`，nodeOutputs 优先于 variables

### 约定

- TOOL 节点类型保留枚举但第一版编译显式拒绝（待后续批次启用）
- 并行分支与 Join 已在第一版收尾（2026-10-15）补齐：见「Phase 8 · 并行分支与 Join」

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

