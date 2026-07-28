package com.vinist.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTargetVO {

    private String id;

    private String type;

    private String name;

    private String description;

    private String status;

    private String unavailableReason;

}
