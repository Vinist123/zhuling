package com.vinist.domain.agent.model;

/**
 * 消息 metadata 字段边界策略
 *
 * <p>Phase 5 只存稳定、轻量、可索引的观测字段，不在 metadata 中重复持久化整段消息正文。
 */
public final class MessageMetadataPolicy {

    private MessageMetadataPolicy() {
    }

    /**
     * metadata 中错误信息的最大长度，超出后截断并打标。
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    /**
     * metadata 中模型名称最大长度。
     */
    public static final int MAX_MODEL_NAME_LENGTH = 128;

    /**
     * metadata 中模型 baseUrl 最大长度。
     */
    public static final int MAX_MODEL_BASE_URL_LENGTH = 256;

    /**
     * 工具入参与出参摘要的最大长度。
     */
    public static final int MAX_TOOL_PREVIEW_LENGTH = 512;

    /**
     * reasoning 能力说明的最大长度。
     */
    public static final int MAX_REASONING_NOTE_LENGTH = 256;

    /**
     * reasoning 原文最大长度。
     */
    public static final int MAX_REASONING_CONTENT_LENGTH = 2048;

}
