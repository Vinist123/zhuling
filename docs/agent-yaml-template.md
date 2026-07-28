# Agent YAML 配置模板

> 将本文档完整内容复制给任意 LLM，告诉它你的需求，即可生成符合 铸灵（ZhuLing） 脚手架规范的 Agent YAML 配置文件。

---

## 使用说明

1. 把下方 **「模板开始」到「模板结束」** 之间的全部内容复制给 LLM
2. 在末尾追加你的具体需求描述，例如：

> 请根据以上模板，帮我生成一个代码审查 Agent 的 YAML 配置。它使用通义千问 qwen-max 模型，API 地址是 `https://dashscope.aliyuncs.com/compatible-mode/v1`，主要职责是审查用户提交的代码并给出改进建议。需要接入一个本地 MCP 工具做代码风格检查。

3. LLM 会输出一份可直接保存为 `.yml` 文件的完整配置

---

## 模板开始（复制以下内容）

---

我需要你帮我生成一份 **铸灵（ZhuLing）** 脚手架的 Agent YAML 配置文件。

请严格遵循以下规范和结构，只输出一份完整的 YAML 配置文件内容，不要输出多余解释。文件内容必须能被直接保存为 `.yml` 文件并被脚手架正确加载。

### 文件位置

文件应放在 `agent-config/agents/` 目录下，文件名建议为 `{agent-id}.yml`。

### 配置结构规范

```yaml
# ===== 基础信息 =====
id: <agent-id>                    # [必填] 运行时唯一标识，建议用英文短横线命名（如 code-reviewer）
app-name: zhuling-app         # [必填] 应用名称，固定为 zhuling-app

agent:
  agent-id: <agent-id>            # [必填] 与顶层 id 保持一致
  agent-name: <中文名>             # [必填] Agent 的可读名称（如：代码审查员）
  agent-desc: |                   # [必填] Agent 描述，同时作为系统提示词（System Prompt）
    <在这里写清楚 Agent 的角色定位、能力边界、输出要求、行为约束>

# ===== 模块配置 =====
module:
  # AI API 连接配置
  ai-api:
    base-url: <API基地址>          # [必填] OpenAI 兼容的 API 基地址
    api-key: <API-Key>            # [必填] API 密钥（用户未提供时用 sk-xxx 占位）

  # 模型选择
  chat-model:
    model: <模型名称>              # [必填] 模型名称（如 gpt-4o、qwen-max、glm-4、deepseek-chat 等）

  # Skills 脚本包（可选，用户未要求时省略此节点）
  skills:
    - name: <skill-name>          # Skill 名称
      enabled: true               # 是否启用
      path: agent-config/skills/<skill-name>  # Skill 包路径
      config:                     # [可选] Skill 自定义参数
        key: value

  # 可观测性（建议全部开启）
  observability:
    react-enabled: true           # ReAct 模式开关
    reasoning-content-enabled: true  # 推理过程展示开关
    tool-call-enabled: true       # 工具调用信息展示开关

  # ReAct 参数（预留）
  react:
    max-steps: 10                 # 最大推理步数
    max-tool-calls: 5             # 最大工具调用次数
    llm-timeout-ms: 30000         # 单次 LLM 调用超时（毫秒）

  # MCP 工具服务（可选，用户未要求时设置 enabled: false）
  mcp:
    enabled: true                 # 是否启用 MCP
    mode: local                   # 默认模式
    servers:
      # Local 模式：本应用内的 Java 工具
      - type: local
        name: <工具组名称>
        tools:
          - name: <工具名>
            enabled: true

      # SSE 模式：远程 MCP Server（按需启用）
      # - type: sse
      #   name: <服务名>
      #   url: <MCP Server URL>
      #   request-timeout-ms: 30000
      #   sse-endpoint: /sse

      # Stdio 模式：命令行 MCP Server（按需启用）
      # - type: stdio
      #   name: <服务名>
      #   command: npx
      #   args: ["-y", "<mcp-package>", "<参数>"]
      #   env:
      #     MCP_LOG_LEVEL: info
      #   request-timeout-ms: 30000

  # 上下文窗口策略
  context:
    max-messages: 20              # 最多携带的历史消息条数
    max-characters: 12000         # 最多携带的历史字符数
    context-window-tokens: <值>   # 模型上下文窗口大小，参考下方表格
```

### 关键规则（必须遵守）

1. **`id` 和 `agent.agent-id` 必须一致**，且在整个 agents 目录中唯一
2. **`agent-desc` 是系统提示词**，要写清楚：
   - Agent 的角色定位（你是谁）
   - 能力边界（能做什么、不能做什么）
   - 输出格式要求（结构化 / Markdown / 简洁等）
   - 行为约束（禁止编造数据、必须使用工具查询等）
3. **`ai-api.base-url`** 支持所有 OpenAI 兼容的 API 服务：
   - OpenAI 官方：`https://api.openai.com/v1`
   - 通义千问：`https://dashscope.aliyuncs.com/compatible-mode/v1`
   - 智谱 GLM：`https://open.bigmodel.cn/api/paas/v4`
   - DeepSeek：`https://api.deepseek.com/v1`
   - Moonshot：`https://api.moonshot.cn/v1`
   - 中转站：填写中转站提供的地址
4. **`chat-model.model`** 填写对应供应商的模型名称
5. **`context.context-window-tokens`** 必须匹配模型的实际上下文窗口大小（参考下方表格）
6. **MCP 三种模式**：
   - `local`：本应用内 Java 实现的工具
   - `sse`：远程 HTTP/SSE MCP Server
   - `stdio`：通过命令行启动的 MCP Server
7. **`skills`** 指向 `agent-config/skills/` 下的目录，目录内必须有 `SKILL.md` 文件
8. YAML 中**不要包含真实 API Key**，用 `sk-xxx` 占位
9. 用户没有提到的可选功能（如 skills、mcp servers），不要凭空编造
10. **本地 Java 工具**（`type: local`）需要开发者预先在 `zhuling-domain` 中用 `@Tool` 注解编写并通过 `ToolCallbackProvider` 注册为 Spring Bean，YAML 中 `tools.name` 对应 Spring Bean 名称。如果用户没有提到需要本地工具，不要凭空编造本地工具配置

### 常见模型上下文窗口参考表

| 模型 | context-window-tokens |
|---|---|
| gpt-4o | 128000 |
| gpt-4o-mini | 128000 |
| gpt-4-turbo | 128000 |
| gpt-3.5-turbo | 16385 |
| qwen-max | 32000 |
| qwen-plus | 131072 |
| qwen-turbo | 131072 |
| glm-4 | 128000 |
| glm-4-flash | 128000 |
| glm-4-plus | 128000 |
| moonshot-v1-8k | 8192 |
| moonshot-v1-32k | 32768 |
| moonshot-v1-128k | 128000 |
| deepseek-chat | 64000 |
| deepseek-coder | 64000 |
| deepseek-v3 | 64000 |
| deepseek-r1 | 64000 |

### 输出要求

- 只输出一份完整的 YAML 配置文件内容
- 不要输出多余解释或说明文字
- 不要使用 Markdown 代码块包裹（不要加 ```yaml ... ```）
- 文件内容必须能被直接保存为 `.yml` 文件并被脚手架正确加载
- 用户未提供的敏感信息（如 API Key）用 `sk-xxx` 占位

---

## 模板结束

---

## 示例对话

### 示例 1：简单对话 Agent

**用户需求**：

> 帮我生成一个通用聊天助手，使用 DeepSeek 模型，API 地址 `https://api.deepseek.com/v1`，API Key 用 `sk-xxx` 占位。职责是友好地回答用户问题，不需要工具。

**LLM 应生成**：

```yaml
id: chat-assistant
app-name: zhuling-app
agent:
  agent-id: chat-assistant
  agent-name: 通用助手
  agent-desc: |
    你是一个友好、专业的 AI 助手。你的职责是：
    1. 准确理解用户问题并给出清晰、有条理的回答
    2. 对于不确定的信息，明确告知用户而非编造
    3. 使用 Markdown 格式组织回答，便于阅读
module:
  ai-api:
    base-url: https://api.deepseek.com/v1
    api-key: sk-xxx
  chat-model:
    model: deepseek-chat
  observability:
    react-enabled: true
    reasoning-content-enabled: true
    tool-call-enabled: true
  react:
    max-steps: 10
    max-tool-calls: 5
    llm-timeout-ms: 30000
  mcp:
    enabled: false
  context:
    max-messages: 20
    max-characters: 12000
    context-window-tokens: 64000
```

### 示例 2：带工具的专业 Agent

**用户需求**：

> 帮我生成一个代码审查 Agent，使用通义千问 qwen-max，API 地址 `https://dashscope.aliyuncs.com/compatible-mode/v1`。职责是审查代码、发现 Bug、给出改进建议。需要接入一个本地 MCP 工具做代码风格检查。

**LLM 应生成**：

```yaml
id: code-reviewer
app-name: zhuling-app
agent:
  agent-id: code-reviewer
  agent-name: 代码审查员
  agent-desc: |
    你是一位资深代码审查员。你的职责是：
    1. 审查用户提交的代码，发现潜在的 Bug、性能问题和安全风险
    2. 给出具体的、可执行的改进建议
    3. 使用 Markdown 格式输出，包含代码示例
    4. 不要编造不存在的代码问题，只针对用户提供的代码进行分析
    5. 对于代码风格问题，使用工具进行检查后再给出结论
module:
  ai-api:
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: sk-xxx
  chat-model:
    model: qwen-max
  observability:
    react-enabled: true
    reasoning-content-enabled: true
    tool-call-enabled: true
  react:
    max-steps: 10
    max-tool-calls: 5
    llm-timeout-ms: 30000
  mcp:
    enabled: true
    mode: local
    servers:
      - type: local
        name: code-style-tools
        tools:
          - name: codeStyleChecker
            enabled: true
  context:
    max-messages: 20
    max-characters: 12000
    context-window-tokens: 32000
```
