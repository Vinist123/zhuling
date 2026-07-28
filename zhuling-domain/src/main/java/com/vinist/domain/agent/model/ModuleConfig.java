package com.vinist.domain.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 模块配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleConfig {

    /** AI API 配置 */
    private AiApiConfig aiApi;

    /** 聊天模型配置 */
    private ChatModelConfig chatModel;

    /** Skills 配置 */
    private List<SkillConfig> skills;

    /** 可观测性配置 */
    private ObservabilityConfig observability;

    /** ReAct 配置 */
    private ReactConfig react;

    /** MCP 配置 */
    private McpConfig mcp;

    /** Runner 配置 */
    private RunnerConfig runner;

    /** 上下文窗口策略 */
    private ContextConfig context;

    /**
     * AI API 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiApiConfig {
        /** 基础 URL */
        private String baseUrl;
        /** API Key */
        private String apiKey;
        /** 对话接口路径 */
        private String completionsPath;
        /** 向量接口路径 */
        private String embeddingsPath;
    }

    /**
     * 聊天模型配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatModelConfig {
        /** 模型名称 */
        private String model;
        /** 额外请求体参数（透传给 LLM API，如 chat_template_kwargs 等） */
        private Map<String, Object> extraBody;
    }

    /**
     * Skill 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillConfig {
        /** Skill 名称 */
        private String name;
        /** 是否启用 */
        private Boolean enabled;
        /** Skill 包路径 */
        private String path;
        /** 配置参数 */
        private java.util.Map<String, Object> config;
    }

    /**
     * 可观测性配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservabilityConfig {
        /** ReAct 模式开关 */
        private Boolean reactEnabled;
        /** 推理过程展示开关 */
        private Boolean reasoningContentEnabled;
        /** 工具调用信息展示开关 */
        private Boolean toolCallEnabled;
    }

    /**
     * ReAct 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReactConfig {
        /** 最大步数 */
        private Integer maxSteps;
        /** 最大工具调用次数 */
        private Integer maxToolCalls;
        /** 单次 LLM 调用超时（毫秒） */
        private Long llmTimeoutMs;
    }

    /**
     * MCP 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpConfig {
        /** 是否启用 */
        private Boolean enabled;
        /** 模式：local/sse/stdio */
        private String mode;
        /** MCP Server 列表 */
        private List<McpServerConfig> servers;
    }

    /**
     * MCP Server 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpServerConfig {
        /** 类型：local/sse/stdio */
        private String type;
        /** 名称 */
        private String name;
        /** SSE 模式的完整 URL 或 baseUrl */
        private String url;
        /** SSE 模式的 endpoint，可选 */
        private String sseEndpoint;
        /** Stdio 模式的命令 */
        private String command;
        /** Stdio 模式的参数 */
        private List<String> args;
        /** Stdio 模式的环境变量 */
        private Map<String, String> env;
        /** 请求超时（毫秒） */
        private Long requestTimeoutMs;
        /** 本地工具列表 */
        private List<LocalToolConfig> tools;
    }

    /**
     * 本地工具配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalToolConfig {
        /** 工具名称 */
        private String name;
        /** 是否启用 */
        private Boolean enabled;
    }

    /**
     * Runner 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunnerConfig {
        /** Runner 名称 */
        private String agentName;
    }

    /**
     * 对话历史裁剪策略。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextConfig {
        /** 最多携带的历史消息条数 */
        private Integer maxMessages;
        /** 最多携带的历史字符数，未配置时不限制 */
        private Integer maxCharacters;
        /** 模型上下文窗口大小（token 数），未配置时不计算占用比例 */
        private Integer contextWindowTokens;
    }

}
