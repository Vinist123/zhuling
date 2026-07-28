package com.vinist.domain.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTarget {

    private String id;

    private ChatTargetType type;

    private String name;

    private String description;

    private String status;

    private String unavailableReason;

}
