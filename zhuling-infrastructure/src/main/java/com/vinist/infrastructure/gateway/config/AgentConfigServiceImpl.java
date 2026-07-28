package com.vinist.infrastructure.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.vinist.domain.agent.model.AgentConfigModel;
import com.vinist.domain.agent.service.IAgentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 配置服务实现
 * 
 * <p>从 agent-config/agents/*.yml 文件读取 Agent 配置并缓存
 */
@Slf4j
@Service
public class AgentConfigServiceImpl implements IAgentConfigService {

    private static final String CONFIG_LOCATION = "classpath*:agent-config/agents/*.yml";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper;
    private final Map<String, AgentConfigModel> configCache = new ConcurrentHashMap<>();

    public AgentConfigServiceImpl(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }

    @PostConstruct
    public void init() {
        loadAllConfigs();
    }

    private void loadAllConfigs() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources(CONFIG_LOCATION);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    loadConfig(resource);
                }
            }
            log.info("加载 {} 个 Agent 配置", configCache.size());
        } catch (IOException e) {
            throw new IllegalStateException("加载 Agent 配置失败: " + CONFIG_LOCATION, e);
        }
    }

    private void loadConfig(Resource resource) throws IOException {
        AgentConfigModel config;
        try (var inputStream = resource.getInputStream()) {
            config = yamlMapper.readValue(inputStream, AgentConfigModel.class);
        } catch (Exception e) {
            throw new IllegalStateException("Agent 配置解析失败: " + resource.getFilename(), e);
        }

        validateConfig(config, resource.getFilename());
        AgentConfigModel previous = configCache.putIfAbsent(config.getId(), config);
        if (previous != null) {
            throw new IllegalStateException("Agent ID 重复: " + config.getId()
                    + "，配置文件: " + resource.getFilename());
        }
        log.info("加载 Agent 配置: id={}, name={}", config.getId(),
                config.getAgent() != null ? config.getAgent().getAgentName() : null);
    }

    private void validateConfig(AgentConfigModel config, String resourceName) {
        if (config == null || config.getId() == null || config.getId().isBlank()) {
            throw new IllegalStateException("Agent 配置缺少 id: " + resourceName);
        }
        if (config.getModule() == null
                || config.getModule().getAiApi() == null
                || config.getModule().getAiApi().getBaseUrl() == null
                || config.getModule().getAiApi().getBaseUrl().isBlank()
                || config.getModule().getAiApi().getApiKey() == null
                || config.getModule().getAiApi().getApiKey().isBlank()
                || config.getModule().getChatModel() == null
                || config.getModule().getChatModel().getModel() == null
                || config.getModule().getChatModel().getModel().isBlank()) {
            throw new IllegalStateException("Agent 配置缺少 module.ai-api 或 module.chat-model.model: "
                    + resourceName);
        }
        validateSkills(config.getModule().getSkills(), resourceName);
    }

    private void validateSkills(List<com.vinist.domain.agent.model.ModuleConfig.SkillConfig> skills,
                                String resourceName) {
        if (skills == null) {
            return;
        }

        Set<String> skillNames = new HashSet<>();
        for (com.vinist.domain.agent.model.ModuleConfig.SkillConfig skill : skills) {
            if (skill == null || !Boolean.TRUE.equals(skill.getEnabled())) {
                continue;
            }
            if (skill.getName() == null || skill.getName().isBlank()
                    || skill.getPath() == null || skill.getPath().isBlank()) {
                throw new IllegalStateException("启用的 Skill 缺少 name 或 path: " + resourceName);
            }
            if (!skillNames.add(skill.getName())) {
                throw new IllegalStateException("Agent 存在重复 Skill 名称: " + skill.getName());
            }
        }
    }

    @Override
    public AgentConfigModel getAgentConfig(String agentId) {
        return configCache.get(agentId);
    }

    @Override
    public List<AgentConfigModel> getAllAgentConfigs() {
        return configCache.values().stream()
                .sorted(java.util.Comparator.comparing(AgentConfigModel::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

}
