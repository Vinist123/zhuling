package com.vinist.infrastructure.gateway.skills;

import com.vinist.domain.agent.model.ModuleConfig;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 为已配置的 Skill 包创建 Spring AI 工具回调。
 */
@Component
public class SkillToolCallbackFactory {

    private final SkillPackageResolver skillPackageResolver;
    private final SkillScriptExecutor skillScriptExecutor = new SkillScriptExecutor();

    public SkillToolCallbackFactory(SkillPackageResolver skillPackageResolver) {
        this.skillPackageResolver = skillPackageResolver;
    }

    public ToolCallback[] build(String agentId, List<ModuleConfig.SkillConfig> configs) {
        Map<String, ResolvedSkillPackage> skillPackages = resolveSkillPackages(agentId, configs);
        if (skillPackages.isEmpty()) {
            return new ToolCallback[0];
        }

        ToolCallback loadSkillCallback = FunctionToolCallback
                .builder("load_skill", (LoadSkillRequest request) -> loadSkill(skillPackages, request))
                .description("加载当前 Agent 已配置的 Skill 使用说明。可用 Skill："
                        + describeSkillPackages(skillPackages))
                .inputType(LoadSkillRequest.class)
                .build();
        String scriptInventory = buildScriptInventory(skillPackages);
        ToolCallback executeSkillCallback = FunctionToolCallback
                .builder("execute_skill_script", (ExecuteSkillScriptRequest request) -> executeSkillScript(skillPackages, request))
                .description("执行当前 Agent 已加载 Skill 包内的 Python 或 Shell 脚本，文件参数使用 workspace:相对路径。"
                        + "\n⚠️ 禁止猜测 scriptPath，必须严格使用以下已登记路径：\n" + scriptInventory
                        + "\n调用前可先调用 load_skill 获取完整使用说明；skillName 必须与下方 Skill 名称一致。")
                .inputType(ExecuteSkillScriptRequest.class)
                .build();
        return new ToolCallback[]{loadSkillCallback, executeSkillCallback};
    }

    private Map<String, ResolvedSkillPackage> resolveSkillPackages(String agentId,
                                                                     List<ModuleConfig.SkillConfig> configs) {
        Map<String, ResolvedSkillPackage> skillPackages = new LinkedHashMap<>();
        for (ModuleConfig.SkillConfig config : configs) {
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                continue;
            }
            ResolvedSkillPackage skillPackage = skillPackageResolver.resolve(agentId, config);
            if (skillPackages.putIfAbsent(skillPackage.name(), skillPackage) != null) {
                throw new IllegalStateException("Agent 存在重复 Skill 名称: " + skillPackage.name());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(skillPackages));
    }

    private String loadSkill(Map<String, ResolvedSkillPackage> skillPackages, LoadSkillRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            List<String> summaries = new ArrayList<>();
            for (ResolvedSkillPackage skillPackage : skillPackages.values()) {
                summaries.add(skillPackage.name() + ": " + skillPackage.description());
            }
            return String.join("\n", summaries);
        }

        ResolvedSkillPackage skillPackage = skillPackages.get(request.name());
        if (skillPackage == null) {
            throw new IllegalArgumentException("当前 Agent 未配置 Skill: " + request.name());
        }
        String scriptsListing = listAvailableScripts(skillPackage);
        return "Skill: " + skillPackage.name() + "\n\n"
                + skillPackage.instructions()
                + "\n\n可用脚本清单（仅以下路径可传给 execute_skill_script 的 scriptPath）：\n"
                + scriptsListing
                + "\n执行约束：调用 execute_skill_script 时仅使用上述脚本路径，禁止猜测；"
                + "所有外部文件参数必须使用 workspace:<相对路径>。";
    }

    private String describeSkillPackages(Map<String, ResolvedSkillPackage> skillPackages) {
        List<String> descriptions = new ArrayList<>();
        for (ResolvedSkillPackage skillPackage : skillPackages.values()) {
            descriptions.add(skillPackage.name() + "（" + skillPackage.description() + "）");
        }
        return String.join("；", descriptions);
    }

    /**
     * 把每个 Skill 包内真实存在的脚本路径内联到工具描述中，
     * 让模型无需猜测 scriptPath（避免臆造 query_singer.py 之类不存在的脚本）。
     */
    private String buildScriptInventory(Map<String, ResolvedSkillPackage> skillPackages) {
        List<String> items = new ArrayList<>();
        for (ResolvedSkillPackage skillPackage : skillPackages.values()) {
            List<String> scripts = new ArrayList<>();
            Path scriptsDir = skillPackage.packageRoot().resolve("scripts");
            if (Files.isDirectory(scriptsDir)) {
                try (Stream<Path> stream = Files.walk(scriptsDir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString();
                                return name.endsWith(".py") || name.endsWith(".sh");
                            })
                            .forEach(p -> scripts.add("scripts/" + scriptsDir.relativize(p)
                                    .toString().replace('\\', '/')));
                } catch (IOException ignored) {
                    // 扫描失败时仅列出 Skill 名称，脚本清单交由 load_skill 兜底
                }
            }
            items.add(skillPackage.name() + (scripts.isEmpty()
                    ? "（无脚本）" : " → " + String.join(", ", scripts)));
        }
        return String.join("；", items);
    }

    private String executeSkillScript(Map<String, ResolvedSkillPackage> skillPackages,
                                      ExecuteSkillScriptRequest request) {
        if (request == null || request.skillName() == null || request.skillName().isBlank()) {
            throw new IllegalArgumentException("执行 Skill 脚本时必须指定 skillName");
        }
        ResolvedSkillPackage skillPackage = skillPackages.get(request.skillName());
        if (skillPackage == null) {
            throw new IllegalArgumentException("当前 Agent 未配置 Skill: " + request.skillName());
        }
        try {
            return skillScriptExecutor.execute(skillPackage, request.scriptPath(), request.arguments());
        } catch (IllegalArgumentException ex) {
            // 脚本路径非法时，把可用清单附在错误信息里，便于模型自我纠正路径
            throw new IllegalArgumentException(ex.getMessage()
                    + "。该 Skill 可用脚本清单：\n" + listAvailableScripts(skillPackage), ex);
        }
    }

    public record LoadSkillRequest(String name) {
    }

    public record ExecuteSkillScriptRequest(String skillName, String scriptPath, List<String> arguments) {
    }

    private String listAvailableScripts(ResolvedSkillPackage skillPackage) {
        Path scriptsDir = skillPackage.packageRoot().resolve("scripts");
        if (!Files.isDirectory(scriptsDir)) {
            return "  (无 scripts 目录)";
        }
        List<String> scripts = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(scriptsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".py") || name.endsWith(".sh");
                    })
                    .forEach(p -> scripts.add(scriptsDir.relativize(p)
                            .toString().replace('\\', '/')));
        } catch (IOException e) {
            return "  (扫描 scripts 目录失败: " + e.getMessage() + ")";
        }
        if (scripts.isEmpty()) {
            return "  (scripts 目录无 .py 或 .sh 文件)";
        }
        Collections.sort(scripts);
        StringBuilder sb = new StringBuilder();
        for (String script : scripts) {
            sb.append("  - scripts/").append(script).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
