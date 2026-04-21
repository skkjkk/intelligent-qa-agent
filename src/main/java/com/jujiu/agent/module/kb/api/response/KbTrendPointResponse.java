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
@Schema(description = "趋势点")
public class KbTrendPointResponse {
    @Schema(description = "日期", example = "2026-04-20")
    private String day;

    @Schema(description = "数量", example = "12")
    private Long count;
}
