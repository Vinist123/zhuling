# 铸灵 ZhuLing

> 铸造 AI 灵魂 —— 基于 SpringBoot 4.1 + Spring AI 2.0 + DDD 六边形架构的 AI Agent 开发脚手架

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21+-green.svg)]()
[![SpringBoot](https://img.shields.io/badge/SpringBoot-4.1.0-brightgreen.svg)]()
[![SpringAI](https://img.shields.io/badge/Spring%20AI-2.0-blueviolet.svg)]()

## 📖 项目简介

铸灵（ZhuLing）帮助 Java 开发者**零代码启动 Agent 应用**。只需编写一份 YAML 配置文件，即可获得：

- ✅ 多 Agent 独立注册与运行时隔离
- ✅ 统一对话目标选择（Agent / Workflow）
- ✅ 同步 & SSE 流式对话接口
- ✅ 多轮会话记忆与历史消息持久化
- ✅ MCP 工具调用（Local / SSE / Stdio 三种模式）
- ✅ Skills 脚本包加载与执行
- ✅ 完整的可观测性（Token 统计、工具遥测、Trace ID、上下文占用比）
- ✅ 版本化 SSE 事件信封（turn / message / reasoning / tool 生命周期事件）
- ✅ 内置可观测对话工作台 UI

## 🖼️ 界面预览

### 登录

![铸灵登录页](docs/img/zl-login.png)

### 对话目标选择

![铸灵对话目标选择](docs/img/zl-index.png)

### 可观测对话工作台

![铸灵可观测对话工作台](docs/img/zl-chat.png)

## 🏗️ 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | 支持虚拟线程 |
| SpringBoot | 4.1.0 | 核心框架 |
| Spring AI | 2.0.1-SNAPSHOT | LLM 调用统一管理 |
| Spring AI Alibaba | 1.1.2.0 | Agent 编排扩展 |
| WebFlux | — | 仅用于对话流式输出 |
| MyBatis-Plus | 3.5.17 | 持久层（传统阻塞式） |
| MySQL | 8.0+ | 会话 / 消息持久化 |
| MCP SDK | 2.0.0-M2 | Model Context Protocol |
| Jackson | 2.17.2 | JSON 序列化 |

## 📁 工程结构

```text
zhuling/
├── zhuling-types/            # 公共类型（枚举、异常、通用常量）
├── zhuling-api/              # 接口 DTO / VO 定义
├── zhuling-domain/           # 领域层（核心业务、Port/Repository 接口）
├── zhuling-case/             # 编排层（用例编排、聊天流程）
├── zhuling-infrastructure/   # 基础设施层（DAO、Gateway、Redis、可观测性实现）
├── zhuling-trigger/          # 触发层（HTTP Controller）
├── zhuling-app/              # 启动模块（配置文件、Agent YAML、Skills）
├── ui/                           # 可观测对话工作台（纯静态前端）
└── docs/                         # 设计文档、SQL 脚本
```

**依赖规则**：`Trigger → API → Case → Domain ← Infrastructure`，所有依赖向内指向 Domain。

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+（或 PostgreSQL 15+）

### 1. 克隆项目

```bash
git clone https://github.com/vinist123/zhuling.git
cd zhuling
```

### 2. 初始化数据库

创建数据库后，执行以下建表 SQL：
```sql
-- Agent 会话表
CREATE TABLE agent_session (
    id VARCHAR(36) PRIMARY KEY,
    target_id VARCHAR(128) NOT NULL,
    target_type VARCHAR(20) NOT NULL DEFAULT 'AGENT',
    user_id VARCHAR(64),
    title VARCHAR(256),
    status VARCHAR(20) DEFAULT 'active',
    message_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Agent 消息表
CREATE TABLE agent_message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES agent_session(id)
);
```

### 3. 修改配置

编辑 `zhuling-app/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    username: root
    password: your_password
    url: jdbc:mysql://localhost:3306/agent_scaffold?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 4. 配置你的 Agent

编辑 `zhuling-app/src/main/resources/agent-config/agents/` 下的 YAML 文件（或新建一个），填写你的模型 API 信息：
```yaml
id: my-agent
app-name: zhuling-app
agent:
  agent-id: my-agent
  agent-name: 我的助手
  agent-desc: 你是一个友好的 AI 助手
module:
  ai-api:
    base-url: https://your-api-provider.com/v1
    api-key: sk-your-api-key
  chat-model:
    model: gpt-4o
  context:
    max-messages: 20
    max-characters: 12000
    context-window-tokens: 128000
```

> 💡 支持所有 OpenAI 兼容的 LLM 服务（OpenAI、通义千问、智谱、Moonshot、各种中转站等）。

### 5. 编译 & 启动

```bash
mvn clean package -DskipTests
cd zhuling-app
java -jar target/zhuling-app-1.0-SNAPSHOT.jar
```

### 6. 验证

- **工作台 UI**：直接用浏览器打开 `ui/index.html`
- **API 测试**：参考下方 [API 接口文档](#-api-接口文档)

## 📋 Agent YAML 配置详解

每个 Agent 对应 `agent-config/agents/` 目录下的一个 YAML 文件，一个文件定义一个 Agent。

### 完整配置模板

```yaml
# ===== 基础信息 =====
id: my-agent                    # [必填] 运行时唯一标识
app-name: zhuling-app       # [必填] 应用名称

agent:
  agent-id: my-agent            # [必填] Agent ID（与 id 一致）
  agent-name: 我的助手            # [必填] 可读名称
  agent-desc: |                 # [必填] Agent 描述，同时作为系统提示词
    你是一个专业的 AI 助手。

# ===== 模块配置 =====
module:
  # AI API 连接
  ai-api:
    base-url: https://api.example.com/v1      # [必填] API 基地址
    api-key: sk-xxx                            # [必填] API Key

  # 模型选择
  chat-model:
    model: gpt-4o                              # [必填] 模型名称

  # Skills 脚本包（可选）
  skills:
    - name: my-skill                           # Skill 名称
      enabled: true                            # 是否启用
      path: agent-config/skills/my-skill       # Skill 包路径
      config:                                  # [可选] Skill 自定义参数
        custom-key: custom-value

  # 可观测性
  observability:
    react-enabled: true                        # ReAct 模式开关
    reasoning-content-enabled: true            # 推理过程展示开关
    tool-call-enabled: true                    # 工具调用信息展示开关

  # ReAct 参数（预留，Phase 7 生效）
  react:
    max-steps: 10                              # 最大推理步数
    max-tool-calls: 5                          # 最大工具调用次数
    llm-timeout-ms: 30000                      # 单次 LLM 调用超时（毫秒）

  # MCP 工具服务（可选）
  mcp:
    enabled: true                              # 是否启用 MCP
    mode: local                                # 默认模式
    servers:
      # Local 模式：本应用内的 Java 工具
      - type: local
        name: my-local-tools
        tools:
          - name: myTool
            enabled: true

      # SSE 模式：远程 MCP Server
      # - type: sse
      #   name: remote-mcp
      #   url: https://mcp-server.example.com
      #   request-timeout-ms: 30000
      #   sse-endpoint: /sse

      # Stdio 模式：命令行 MCP Server
      # - type: stdio
      #   name: stdio-tools
      #   command: npx
      #   args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
      #   env:
      #     MCP_LOG_LEVEL: info
      #   request-timeout-ms: 30000

  # 上下文窗口策略
  context:
    max-messages: 20                           # 最多携带的历史消息条数
    max-characters: 12000                      # 最多携带的历史字符数
    context-window-tokens: 128000              # 模型上下文窗口大小（token 数）
```

### 配置校验规则

启动时会自动校验：
- `id`、`agent.agent-id` 不能为空
- Agent ID 不能重复
- `ai-api.base-url`、`ai-api.api-key`、`chat-model.model` 为必填
- 引用的 Skill 包路径必须存在
- 配置语法错误或缺少必填项时**启动失败**
- 外部 MCP 连接失败时仅将对应 Agent 标记为 `UNAVAILABLE`，不影响其他 Agent

> 📝 **提示**：你可以将 [Agent YAML 配置模板](docs/agent-yaml-template.md) 丢给 LLM，让它根据你的需求自动生成配置文件。

## 🔨 本地 Java 工具开发

脚手架支持用 Java 编写本地工具，通过 `@Tool` 注解暴露给 LLM 调用。完整流程如下：

### 第一步：编写工具类

在 `zhuling-domain` 模块中创建工具类，使用 `@Tool` 注解标注方法：

```java
package com.vinist.domain.agent.service.matter.mcp.server;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MyCustomToolService {

    @Tool(description = "查询用户订单信息，传入用户ID，返回订单列表")
    public OrderResult queryOrder(OrderRequest request) {
        log.info("查询订单: userId={}", request.getUserId());
        // 实现你的业务逻辑
        return new OrderResult();
    }
}
```

**要点**：
- 类上加 `@Service`，让 Spring 管理
- 方法上加 `@Tool(description = "...")`，描述会告诉 LLM 这个工具能做什么
- 参数和返回值使用 POJO，Spring AI 会自动序列化/反序列化
- 用 `@JsonPropertyDescription` 标注参数含义，帮助 LLM 理解如何传参

### 第二步：注册为 ToolCallbackProvider

在 `zhuling-app` 的 `Application.java` 中注册：

```java
@Bean("myCustomToolService")
public ToolCallbackProvider myCustomTools(MyCustomToolService toolService) {
    return MethodToolCallbackProvider.builder().toolObjects(toolService).build();
}
```

> Bean 名称（如 `myCustomToolService`）就是 YAML 配置中引用的名称。

### 第三步：在 Agent YAML 中配置

```yaml
module:
  mcp:
    enabled: true
    servers:
      - type: local
        name: my-custom-tools
        tools:
          - name: myCustomToolService   # 对应 Spring Bean 名称
            enabled: true
```

### 注册流程

```
@Tool 方法 → @Service Bean → ToolCallbackProvider → LocalMcpToolCallbackBuilder → AgentRuntime → LLM 可调用
```

启动时，`AgentRuntimeFactory` 读取 YAML 中的 `mcp.servers[type=local]`，通过 `LocalMcpToolCallbackBuilder` 从 Spring 容器中查找 Bean，自动完成工具注册。Agent 对话时，LLM 即可自动识别并调用这些工具。

## 🔧 Skills 开发

Skill 是一个包含 `SKILL.md` 和可执行脚本的目录：

```
agent-config/skills/my-skill/
├── SKILL.md          # Skill 描述文件（必须）
├── scripts/          # 可执行脚本
│   └── main.py
└── data/             # 静态数据文件（可选）
    └── catalog.json
```

`SKILL.md` 示例：

```markdown
---
name: my-skill
description: 查询我的业务数据
---

# 数据查询

1. 调用 `execute_skill_script`
2. 使用 `skillName: "my-skill"`
3. 使用 `scriptPath: "scripts/main.py"`
4. `arguments` 传入查询参数
5. 根据脚本返回的 JSON 回答
```

## 📡 API 接口文档

### 对话目标

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/chat-targets` | 获取所有可用对话目标 |
| GET | `/api/v1/chat-targets/{type}/{id}` | 获取目标详情 |

### 会话管理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/session/create` | 创建会话 |
| GET | `/api/v1/session?userId=xxx` | 分页查询会话列表 |
| GET | `/api/v1/session/{sessionId}` | 获取会话详情 |
| GET | `/api/v1/session/{sessionId}/messages` | 获取会话消息列表 |
| POST | `/api/v1/session/{sessionId}/close` | 关闭会话 |

### 聊天

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/chat/sync` | 同步聊天 |
| POST | `/api/v1/chat/stream` | 流式聊天（SSE） |

### 工具调试

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/tools?targetId=xxx` | 查看已注册工具 |
| POST | `/api/v1/tools/execute` | 手动执行工具 |

### 请求/响应示例

**创建会话**：

```bash
curl -X POST http://localhost:8091/api/v1/session/create \
  -H "Content-Type: application/json" \
  -d '{
    "targetId": "default-agent",
    "targetType": "AGENT",
    "userId": "user-001",
    "title": "测试会话"
  }'
```

**同步聊天**：

```bash
curl -X POST http://localhost:8091/api/v1/chat/sync \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "上一步返回的sessionId",
    "message": "你好"
  }'
```

**流式聊天（SSE）**：

```bash
curl -N -X POST http://localhost:8091/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "上一步返回的sessionId",
    "message": "你好"
  }'
```

流式事件类型：

| 事件 | 说明 |
|---|---|
| `turn.started` | 本轮对话开始，包含 Target、模型、Trace 信息 |
| `message.delta` | 文本增量 |
| `reasoning.delta` | 推理过程增量（供应商支持时） |
| `tool.started` | 工具开始执行 |
| `tool.completed` | 工具执行完成 |
| `turn.completed` | 本轮对话完成，包含完整 metadata |
| `turn.failed` | 本轮对话失败，包含错误信息 |

## 🖥️ 可观测对话工作台

项目内置了一个纯静态的对话工作台（`ui/index.html`），无需部署，直接浏览器打开即可使用。

功能包括：
- **Target 选择**：下拉选择可用的 Agent
- **会话管理**：创建、切换、关闭会话
- **实时对话**：流式展示推理过程、工具调用、文本输出
- **历史回放**：从持久化的 metadata 还原可观测摘要
- **资源检查器**：查看 Trace ID、Token 消耗、上下文占用比、工具遥测


## 🗺️ 开发路线

| 阶段 | 任务 | 状态 |
|---|---|---|
| Phase 1 | 基础框架搭建 | ✅ |
| Phase 2 | LLM 集成与配置 | ✅ |
| Phase 3 | 会话管理 | ✅ |
| Phase 4 | 工具调用 | ✅ |
| Phase 5 | 可观测性 | ✅ |
| Phase 6 | MCP 支持 | ✅ |
| Phase 6.5 | 可观测性增强 | ✅ |
| Phase 6.6 | 多 Agent 运行时基础 | ✅ |
| Phase 7 | ReAct 模式 | ⏳ |
| Phase 8 | 多 Agent 协同 | ⏳ |
| Phase 9 | 多模态支持 | ⏳ |
| Phase 10 | 测试与文档 | ⏳ |

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 提交 Pull Request

## 🙏 致谢

本项目的 DDD 分层架构组织与「配置即 Agent」的设计理念，受 [@小傅哥](https://github.com/fuzhengwei) 的开源作品与系列课程启发，特此致谢。

> 本项目为独立设计与实现，仅在架构思路层面受其影响，未直接复用其代码。

## 📄 开源协议

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## 📮 联系作者

- **Author**：Vinist
- **Email**：haodi0312@163.com
- **GitHub**：[github.com/vinist123](https://github.com/vinist123)

