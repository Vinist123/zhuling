package com.vinist.infrastructure.gateway.skills;

import java.nio.file.Path;

/**
 * 已解析的 Skill 包。
 */
public record ResolvedSkillPackage(
        String name,
        String description,
        String instructions,
        Path packageRoot,
        Path workspaceRoot) {
}
