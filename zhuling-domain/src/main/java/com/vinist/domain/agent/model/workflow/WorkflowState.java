package com.vinist.domain.agent.model.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow 黑板状态（Phase 8 决策：简单状态 + Java 组件承担复杂逻辑）。
 *
 * <p>仅两级结构：
 * <ul>
 *   <li>{@code variables}：Workflow 级共享变量（YAML state 段初始化）</li>
 *   <li>{@code nodeOutputs}：各节点输出，以 outputKey 写入</li>
 * </ul>
 * 模板解析时 {@code nodeOutputs} 优先于 {@code variables}（后写覆盖先写）。</p>
 */
public class WorkflowState {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();

    /**
     * Join 屏障计数器：nodeId -> 已到达的并行分支数。仅并行汇聚节点使用，
     * 由调度器以 {@link #arriveJoinBarrier} 原子累加，达到 requiredCount 的最后一个分支
     * 才获准执行汇聚节点，其余分支直接终止（避免汇聚节点被重复执行）。
     */
    private final Map<String, AtomicInteger> joinArrivals = new ConcurrentHashMap<>();

    public WorkflowState() {
    }

    public WorkflowState(Map<String, Object> initialVariables) {
        if (initialVariables != null) {
            variables.putAll(initialVariables);
        }
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    public Object getNodeOutput(String key) {
        return nodeOutputs.get(key);
    }

    public void setNodeOutput(String key, Object value) {
        nodeOutputs.put(key, value);
    }

    /**
     * 模板解析取值绑定：nodeOutputs 优先于 variables。
     */
    public Object resolveBinding(String key) {
        Object output = nodeOutputs.get(key);
        return output != null ? output : variables.get(key);
    }

    /**
     * 渲染 {@code ${key}} 占位符模板；未命中的占位符原样保留。
     * 绑定值统一 toString（Agent 输入为文本）。
     */
    public String renderTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = resolveBinding(matcher.group(1).trim());
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(value == null ? matcher.group(0) : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 黑板快照（用于观测与持久化） */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new ConcurrentHashMap<>(variables);
        snapshot.putAll(nodeOutputs);
        return snapshot;
    }

    /**
     * Join 屏障签到：原子累加该汇聚节点的到达分支数，返回是否已达到所需数量
     * （达到者即"最后一个分支"，获准执行汇聚节点）。
     *
     * @param nodeId        汇聚节点 id
     * @param requiredCount 需到达的并行分支总数（= 该节点无条件入边数）
     * @return true 表示本分支为最后到达者，应执行汇聚节点；false 表示其他分支尚未到齐，本分支终止
     */
    public boolean arriveJoinBarrier(String nodeId, int requiredCount) {
        AtomicInteger counter = joinArrivals.computeIfAbsent(nodeId, k -> new AtomicInteger(0));
        return counter.incrementAndGet() == requiredCount;
    }

}
