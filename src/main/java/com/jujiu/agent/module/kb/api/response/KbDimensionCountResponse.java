package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "维度计数")
public class KbDimensionCountResponse {
    @Schema(description = "维度名称", example = "pdf")
    private String name;

    @Schema(description = "数量", example = "18")
    private Long count;
}
