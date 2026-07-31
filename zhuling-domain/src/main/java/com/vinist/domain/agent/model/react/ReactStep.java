package com.vinist.domain.agent.model.react;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactStep {

    private int index;

    private ReactModelResponse modelResponse;

    @Builder.Default
    private List<ReactToolResult> toolResults = List.of();

}
