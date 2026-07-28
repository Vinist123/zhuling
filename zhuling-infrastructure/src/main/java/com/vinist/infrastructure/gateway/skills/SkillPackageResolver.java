package com.vinist.infrastructure.gateway.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.vinist.domain.agent.model.ModuleConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 将 Agent 配置引用的 Skill 包解析为可执行目录。
 */
@Component
public class SkillPackageResolver {

    private static final Path RUNTIME_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "zhuling", "agents");

    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public SkillPackageResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public ResolvedSkillPackage resolve(String agentId, ModuleConfig.SkillConfig config) {
        try {
            Path packageRoot = resolvePackageRoot(agentId, config);
            SkillDocument skillDocument = readSkillDocument(packageRoot, config.getName());
            Path workspaceRoot = resolveAgentRoot(agentId).resolve("workspace");
            Files.createDirectories(workspaceRoot);
            return new ResolvedSkillPackage(skillDocument.name(), skillDocument.description(),
                    skillDocument.instructions(), packageRoot, workspaceRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Skill 包解析失败: " + config.getName(), e);
        }
    }

    private Path resolvePackageRoot(String agentId, ModuleConfig.SkillConfig config) throws IOException {
        if (config.getPath().startsWith("file:")) {
            Path packageRoot = Path.of(URI.create(config.getPath())).toRealPath();
            if (!Files.isDirectory(packageRoot)) {
                throw new IllegalStateException("Skill 路径不是目录: " + config.getPath());
            }
            return packageRoot;
        }
        return extractClasspathPackage(agentId, config.getPath());
    }

    private Path extractClasspathPackage(String agentId, String configuredPath) throws IOException {
        String resourcePath = normalizeResourcePath(configuredPath);
        Path skillPackagesRoot = resolveAgentRoot(agentId).resolve("skill-packages");
        Files.createDirectories(skillPackagesRoot);
        Path packageDirectory = Files.createTempDirectory(skillPackagesRoot, "skill-");
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(resourceLoader);
        Resource[] resources = resolver.getResources("classpath*:" + resourcePath + "/**");

        int copiedResources = 0;
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            String relativePath = resolveRelativeResourcePath(resource, resourcePath);
            if (relativePath.isBlank()) {
                continue;
            }

            Path target = packageDirectory.resolve(relativePath).normalize();
            if (!target.startsWith(packageDirectory)) {
                throw new IllegalStateException("Skill 资源路径越界: " + relativePath);
            }
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            copiedResources++;
        }

        if (copiedResources == 0) {
            throw new IllegalStateException("未找到 Skill classpath 资源: " + configuredPath);
        }
        return packageDirectory.toRealPath();
    }

    private String normalizeResourcePath(String configuredPath) {
        String resourcePath = configuredPath.replace('\\', '/');
        if (resourcePath.startsWith("classpath:")) {
            resourcePath = resourcePath.substring("classpath:".length());
        }
        while (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        if (resourcePath.isBlank() || resourcePath.contains("..")) {
            throw new IllegalArgumentException("非法 Skill classpath 路径: " + configuredPath);
        }
        return resourcePath;
    }

    private String resolveRelativeResourcePath(Resource resource, String resourcePath) throws IOException {
        String resourceUrl = resource.getURL().toExternalForm().replace('\\', '/');
        int rootIndex = resourceUrl.indexOf(resourcePath);
        if (rootIndex < 0) {
            throw new IllegalStateException("无法计算 Skill 资源相对路径: " + resourceUrl);
        }
        String relativePath = resourceUrl.substring(rootIndex + resourcePath.length());
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return relativePath;
    }

    private SkillDocument readSkillDocument(Path packageRoot, String configuredName) throws IOException {
        Path skillFile = packageRoot.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            throw new IllegalStateException("Skill 包缺少 SKILL.md: " + configuredName);
        }

        String content = Files.readString(skillFile);
        String normalizedContent = content.replace("\r\n", "\n");
        if (!normalizedContent.startsWith("---\n")) {
            throw new IllegalStateException("SKILL.md 缺少 YAML front matter: " + configuredName);
        }

        int frontMatterEnd = normalizedContent.indexOf("\n---", 4);
        if (frontMatterEnd < 0) {
            throw new IllegalStateException("SKILL.md YAML front matter 未闭合: " + configuredName);
        }
        int instructionsStart = frontMatterEnd + 4;
        if (instructionsStart < normalizedContent.length()
                && normalizedContent.charAt(instructionsStart) == '\n') {
            instructionsStart++;
        }

        Map<String, Object> frontMatter = yamlMapper.readValue(normalizedContent.substring(4, frontMatterEnd),
                new TypeReference<>() {
                });
        Object documentName = frontMatter.get("name");
        if (!(documentName instanceof String skillName) || skillName.isBlank()) {
            throw new IllegalStateException("SKILL.md 缺少 name: " + configuredName);
        }
        if (!configuredName.equals(skillName)) {
            throw new IllegalStateException("Skill 配置名称与 SKILL.md 名称不一致: " + configuredName);
        }
        Object documentDescription = frontMatter.get("description");
        String description = documentDescription instanceof String value ? value : "";
        return new SkillDocument(skillName, description, normalizedContent.substring(instructionsStart).strip());
    }

    private Path resolveAgentRoot(String agentId) throws IOException {
        Path agentRoot = RUNTIME_ROOT.resolve(agentId).normalize();
        if (!agentRoot.startsWith(RUNTIME_ROOT)) {
            throw new IllegalArgumentException("非法 Agent ID: " + agentId);
        }
        Files.createDirectories(agentRoot);
        return agentRoot;
    }

    private record SkillDocument(String name, String description, String instructions) {
    }
}
