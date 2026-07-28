package com.vinist.domain.agent.model.telemetry;

/**
 * reasoning_content（思维链）可获取性状态
 *
 * <p>用于标注当前单轮调用中 LLM 返回的思维链数据是否可靠，供下游消费方做展示/降级决策：
 * <ul>
 *   <li>{@link #STABLE} — 已稳定获取到完整 reasoning_content，可放心展示</li>
 *   <li>{@link #UNSTABLE} — 模型可能支持 reasoning，但本次未获取到或获取不完整</li>
 *   <li>{@link #UNSUPPORTED} — 模型/框架不支持 reasoning_content</li>
 * </ul>
 */
public enum ReasoningContentStatus {

    /** 已稳定获取到 reasoning_content */
    STABLE,

    /** 未能获取 reasoning_content，但模型可能支持（如非推理模型、thinking 开关关闭等） */
    UNSTABLE,

    /** 明确不支持 reasoning_content */
    UNSUPPORTED;

    /**
     * 序列化为 metadata 中的字符串值。
     */
    public String toValue() {
        return name().toLowerCase();
    }

    /**
     * 兼容旧版字符串值的反解析。
     */
    public static ReasoningContentStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return UNSUPPORTED;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNSUPPORTED;
        }
    }
}
