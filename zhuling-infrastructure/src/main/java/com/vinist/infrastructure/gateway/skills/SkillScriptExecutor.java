package com.vinist.infrastructure.gateway.skills;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 仅执行已加载 Skill 包内的受信任脚本。
 */
public class SkillScriptExecutor {

    private static final long TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_BYTES = 16 * 1024;
    private static final String WORKSPACE_PREFIX = "workspace:";

    public String execute(ResolvedSkillPackage skill, String scriptPath, List<String> arguments) {
        Path script = resolveScript(skill, scriptPath);
        List<String> command = new ArrayList<>();
        command.add(resolveInterpreter(script));
        command.add(script.toString());
        for (String argument : arguments == null ? List.<String>of() : arguments) {
            command.add(resolveArgument(skill.workspaceRoot(), argument));
        }

        try {
            Process process = new ProcessBuilder(command)
                    .directory(skill.packageRoot().toFile())
                    .redirectErrorStream(true)
                    .start();
            try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                Future<OutputCapture> outputFuture = executor.submit(() -> readOutput(process.getInputStream()));
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("Skill 脚本执行超时: " + scriptPath);
                }

                OutputCapture output = outputFuture.get(5, TimeUnit.SECONDS);
                if (process.exitValue() != 0) {
                    throw new IllegalStateException("Skill 脚本执行失败: " + scriptPath
                            + "，exitCode=" + process.exitValue() + "，output=" + output.content());
                }
                return output.content();
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 Skill 脚本: " + scriptPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Skill 脚本执行被中断: " + scriptPath, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("读取 Skill 脚本输出失败: " + scriptPath, e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("读取 Skill 脚本输出超时: " + scriptPath, e);
        }
    }

    private Path resolveScript(ResolvedSkillPackage skill, String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            throw new IllegalArgumentException("Skill 脚本路径不能为空");
        }
        Path requestedPath = Path.of(scriptPath);
        if (requestedPath.isAbsolute() || containsParentSegment(requestedPath)) {
            throw new IllegalArgumentException("Skill 脚本路径必须位于 Skill 包内");
        }

        Path script = skill.packageRoot().resolve(requestedPath).normalize();
        if (!script.startsWith(skill.packageRoot()) || !Files.isRegularFile(script)) {
            throw new IllegalArgumentException("脚本不在 Skill 包内或不存在: " + scriptPath);
        }
        return script;
    }

    private String resolveInterpreter(Path script) {
        String filename = script.getFileName().toString();
        if (filename.endsWith(".py")) {
            return "python3";
        }
        if (filename.endsWith(".sh")) {
            return "bash";
        }
        throw new IllegalArgumentException("仅支持执行 .py 或 .sh Skill 脚本: " + filename);
    }

    private String resolveArgument(Path workspaceRoot, String argument) {
        if (argument == null) {
            throw new IllegalArgumentException("Skill 脚本参数不能为 null");
        }
        if (!argument.startsWith(WORKSPACE_PREFIX)) {
            Path literalPath = Path.of(argument);
            if (literalPath.isAbsolute() || containsParentSegment(literalPath)) {
                throw new IllegalArgumentException("外部文件参数必须使用 workspace: 前缀");
            }
            return argument;
        }

        String relativeValue = argument.substring(WORKSPACE_PREFIX.length());
        if (relativeValue.isBlank()) {
            throw new IllegalArgumentException("workspace: 参数缺少相对路径");
        }
        Path relativePath = Path.of(relativeValue);
        if (relativePath.isAbsolute() || containsParentSegment(relativePath)) {
            throw new IllegalArgumentException("workspace: 参数不能使用绝对路径或 ..");
        }

        Path resolvedPath = workspaceRoot.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("workspace: 参数路径越界");
        }
        try {
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 Skill 工作目录", e);
        }
        return resolvedPath.toString();
    }

    private boolean containsParentSegment(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private OutputCapture readOutput(InputStream inputStream) throws IOException {
        try (inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            boolean truncated = false;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                int remaining = MAX_OUTPUT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(read, remaining));
                }
                if (read > remaining) {
                    truncated = true;
                }
            }
            String content = output.toString(StandardCharsets.UTF_8);
            return new OutputCapture(truncated ? content + "\n[输出已截断]" : content);
        }
    }

    private record OutputCapture(String content) {
    }
}
