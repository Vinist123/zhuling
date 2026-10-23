package com.vinist.infrastructure.runtime.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.vinist.domain.agent.model.AgentRuntime;
import com.vinist.domain.agent.model.workflow.IWorkflowCodeNode;
import com.vinist.domain.agent.model.workflow.IWorkflowCondition;
import com.vinist.domain.agent.model.workflow.WorkflowConfigModel;
import com.vinist.domain.agent.model.workflow.WorkflowDefinition;
import com.vinist.domain.agent.model.workflow.WorkflowNode;
import com.vinist.domain.agent.model.workflow.WorkflowNodeType;
import com.vinist.domain.agent.model.workflow.WorkflowRuntime;
import com.vinist.domain.agent.service.IAgentRuntimeRegistry;
import com.vinist.domain.agent.service.IWorkflowConfigService;
import com.vinist.domain.agent.service.IWorkflowNodeRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 配置服务实现：workflows/*.yml 加载、校验、编译与引用解析。
 *
 * <p>加载策略（与 AgentRuntimeRegistryImpl 的 UNAVAILABLE 降级保持一致）：
 * <b>按文件 fail-soft</b>——启动期仍会对每个 yml 做完整编译与引用校验（"在启动时检查声明"），
 * 但单个配置失败只会 {@code log.error} 后跳过该 Workflow，<b>不影响其他 Agent / Workflow 启动</b>；
 * 失败的 Workflow 不会出现在目标列表里。可通过 {@code zhuling.workflow.strict=true} 切回
 * fail-fast（任一处失败即终止启动，保留原半可用态防护）。workflows 目录为空时不报错。</p>
 */
@Slf4j
@Service
public class WorkflowConfigServiceImpl implements IWorkflowConfigService {

    private static final String CONFIG_LOCATION = "classpath*:agent-config/workflows/*.yml";

    private final ResourceLoader resourceLoader;
    private final IAgentRuntimeRegistry agentRuntimeRegistry;
    private final IWorkflowNodeRegistry nodeRegistry;
    private final WorkflowCompiler workflowCompiler;
    private final ObjectMapper yamlMapper;
    private final boolean strict;
    private final Map<String, WorkflowRuntime> runtimeCache = new LinkedHashMap<>();

    public WorkflowConfigServiceImpl(ResourceLoader resourceLoader,
                                     IAgentRuntimeRegistry agentRuntimeRegistry,
                                     IWorkflowNodeRegistry nodeRegistry,
                                     WorkflowCompiler workflowCompiler,
                                     @Value("${zhuling.workflow.strict:false}") boolean strict) {
        this.resourceLoader = resourceLoader;
        this.agentRuntimeRegistry = agentRuntimeRegistry;
        this.nodeRegistry = nodeRegistry;
        this.workflowCompiler = workflowCompiler;
        this.strict = strict;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }

    @PostConstruct
    public void init() {
        loadAllWorkflows();
    }

    private void loadAllWorkflows() {
        Resource[] resources;
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver(resourceLoader);
            resources = resolver.getResources(CONFIG_LOCATION);
        } catch (IOException e) {
            if (strict) {
                throw new IllegalStateException("加载 Workflow 配置失败: " + CONFIG_LOCATION, e);
            }
            log.error("加载 Workflow 配置失败（已忽略，服务继续启动，strict=false）: {} -> {}",
                    CONFIG_LOCATION, e.getMessage());
            return;
        }

        Map<String, String> failed = new LinkedHashMap<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try {
                if (resource.isReadable()) {
                    loadWorkflow(resource);
                }
            } catch (Exception e) {
                String message = safeMessage(e);
                failed.put(filename, message);
                if (strict) {
                    throw new IllegalStateException(
                            "Workflow 配置校验失败（strict 模式，终止启动）: " + filename
                                    + " -> " + message, e);
                }
                log.error("Workflow 配置校验失败，已跳过该 Workflow（不影响其他 Agent/Workflow 启动）: "
                        + "{} -> {}", filename, message);
            }
        }

        if (failed.isEmpty()) {
            log.info("加载并编译 {} 个 Workflow: {}", runtimeCache.size(), runtimeCache.keySet());
        } else {
            log.warn("Workflow 加载完成：成功 {} 个（{}），跳过 {} 个（{}）。"
                            + " 如需严格模式（任一失败即终止启动）请设置 zhuling.workflow.strict=true",
                    runtimeCache.size(), runtimeCache.keySet(), failed.size(), failed.keySet());
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName() : message;
    }


    private void loadWorkflow(Resource resource) throws IOException {
        String filename = resource.getFilename();
        WorkflowConfigModel config;
        try (var inputStream = resource.getInputStream()) {
            config = yamlMapper.readValue(inputStream, WorkflowConfigModel.class);
        } catch (Exception e) {
            throw new IllegalStateException("Workflow 配置解析失败: " + filename, e);
        }

        WorkflowDefinition definition = workflowCompiler.compile(config, filename);
        if (runtimeCache.putIfAbsent(definition.getId(), resolveRuntime(definition)) != null) {
            throw new IllegalStateException("Workflow ID 重复: " + definition.getId()
                    + "，配置文件: " + filename);
        }
        log.info("编译 Workflow: id={}, name={}, 节点数={}", definition.getId(),
                definition.getName(), definition.getNodes().size());
    }

    /** 解析节点引用：AGENT -> AgentRuntime，CODE/CONDITION -> 注册组件 */
    private WorkflowRuntime resolveRuntime(WorkflowDefinition definition) {
        Map<String, AgentRuntime> agentNodes = new LinkedHashMap<>();
        Map<String, IWorkflowCodeNode> codeNodes = new LinkedHashMap<>();
        Map<String, IWorkflowCondition> conditionNodes = new LinkedHashMap<>();

        for (WorkflowNode node : definition.getNodes().values()) {
            if (node.getType() == WorkflowNodeType.AGENT) {
                agentNodes.put(node.getNodeId(),
                        resolveAgent(node, definition.getId()));
            } else if (node.getType() == WorkflowNodeType.CODE) {
                IWorkflowCodeNode component = nodeRegistry.getCodeNode(node.getComponent());
                if (component == null) {
                    throw new IllegalStateException("CODE 组件未注册: " + node.getComponent()
                            + "，节点=" + node.getNodeId() + "，workflow=" + definition.getId());
                }
                codeNodes.put(node.getNodeId(), component);
            } else if (node.getType() == WorkflowNodeType.CONDITION) {
                IWorkflowCondition component = nodeRegistry.getCondition(node.getComponent());
                if (component == null) {
                    throw new IllegalStateException("CONDITION 组件未注册: " + node.getComponent()
                            + "，节点=" + node.getNodeId() + "，workflow=" + definition.getId());
                }
                conditionNodes.put(node.getNodeId(), component);
            }
        }

        return WorkflowRuntime.builder()
                .workflowId(definition.getId())
                .definition(definition)
                .agentNodes(agentNodes)
                .codeNodes(codeNodes)
                .conditionNodes(conditionNodes)
                .build();
    }

    private AgentRuntime resolveAgent(WorkflowNode node, String workflowId) {
        try {
            return agentRuntimeRegistry.getRequired(node.getAgentId());
        } catch (RuntimeException e) {
            throw new IllegalStateException("AGENT 节点引用的 Agent 不可用: "
                    + node.getAgentId() + "，节点=" + node.getNodeId()
                    + "，workflow=" + workflowId, e);
        }
    }

    @Override
    public WorkflowRuntime getRequired(String workflowId) {
        WorkflowRuntime runtime = runtimeCache.get(workflowId);
        if (runtime == null) {
            throw new IllegalArgumentException("Workflow runtime not found: " + workflowId);
        }
        return runtime;
    }

    @Override
    public List<WorkflowRuntime> getAllWorkflowRuntimes() {
        return runtimeCache.values().stream()
                .sorted(Comparator.comparing(WorkflowRuntime::getWorkflowId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

}
