package com.vinist.domain.agent.model.telemetry;

/**
 * 单轮对话流事件类型。
 */
public enum ConversationStreamEventType {

    TURN_STARTED("turn.started"),
    MESSAGE_DELTA("message.delta"),
    REASONING_DELTA("reasoning.delta"),
    TOOL_STARTED("tool.started"),
    TOOL_COMPLETED("tool.completed"),
    TURN_COMPLETED("turn.completed"),
    TURN_FAILED("turn.failed");

    private final String wireName;

    ConversationStreamEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public boolean isTerminal() {
        return this == TURN_COMPLETED || this == TURN_FAILED;
    }

}
