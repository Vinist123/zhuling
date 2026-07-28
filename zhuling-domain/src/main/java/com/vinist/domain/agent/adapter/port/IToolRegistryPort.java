package com.vinist.domain.agent.adapter.port;

import com.vinist.domain.agent.model.ModuleConfig;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 工具注册端口接口
 * 
 * <p>Domain 层定义，Infrastructure 层实现
 * 统一管理三种工具来源：
 * <ul>
 *   <li>本地工具：通过 @Tool 注解的方法，由 MethodToolCallbackProvider 注册</li>
 *   <li>MCP Server：通过 SSE/Stdio 连接的远程 MCP Server</li>
 *   <li>Skills：通过 YAML/Resource 文件定义的工具集</li>
 * </ul>
 */
public interface IToolRegistryPort {

    /**
     * 注册本地工具（@Tool 注解的 Bean）
     * 
     * @param toolName Bean 名称
     */
    void registerLocalTool(String toolName);

    /**
     * 注册 MCP Server 工具
     * 
     * @param mcpConfig MCP 配置
     */
    void registerMcpTools(ModuleConfig.McpConfig mcpConfig);

    /**
     * 注册 Skills 工具
     * 
     * @param skillsConfig Skills 配置
     */
    void registerSkillsTools(List<ModuleConfig.SkillConfig> skillsConfig);

    /**
     * 获取所有已注册的工具
     * 
     * @return 工具数组
     */
    ToolCallback[] getAllToolCallbacks();

    /**
     * 根据工具名称获取工具回调
     *
     * @param toolName 工具名称
     * @return 工具回调，不存在时返回 null
     */
    ToolCallback getToolCallback(String toolName);

    /**
     * 获取已注册工具名称列表
     *
     * @return 工具名称列表
     */
    List<String> getRegisteredToolNames();

}
