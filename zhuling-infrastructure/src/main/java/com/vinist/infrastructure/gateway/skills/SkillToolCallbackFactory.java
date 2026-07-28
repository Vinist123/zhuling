package com.vinist.infrastructure.gateway.skills;

import com.vinist.domain.agent.model.ModuleConfig;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        ToolCallback executeSkillCallback = FunctionToolCallback
                .builder("execute_skill_script", (ExecuteSkillScriptRequest request) -> executeSkillScript(skillPackages, request))
                .description("执行已加载 Skill 包内的 Python 或 Shell 脚本，文件参数使用 workspace:相对路径")
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
        return "Skill: " + skillPackage.name() + "\n\n"
                + skillPackage.instructions()
                + "\n\n执行约束：调用 execute_skill_script 时仅使用本 Skill 包内的 .py 或 .sh 相对路径；"
                + "所有外部文件参数必须使用 workspace:<相对路径>。";
    }

    private String describeSkillPackages(Map<String, ResolvedSkillPackage> skillPackages) {
        List<String> descriptions = new ArrayList<>();
        for (ResolvedSkillPackage skillPackage : skillPackages.values()) {
            descriptions.add(skillPackage.name() + "（" + skillPackage.description() + "）");
        }
        return String.join("；", descriptions);
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
        return skillScriptExecutor.execute(skillPackage, request.scriptPath(), request.arguments());
    }

    public record LoadSkillRequest(String name) {
    }

    public record ExecuteSkillScriptRequest(String skillName, String scriptPath, List<String> arguments) {
    }
}
