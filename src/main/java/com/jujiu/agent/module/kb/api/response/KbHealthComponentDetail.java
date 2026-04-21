package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "健康检查组件明细")
public class KbHealthComponentDetail {
    @Schema(description = "组件名", example = "minio")
    private String component;

    @Schema(description = "状态", example = "DEGRADED")
    private String status; // UP / DEGRADED / DOWN

    @Schema(description = "检查类型", example = "capability")
    private String checkType; // connectivity / capability

    @Schema(description = "耗时毫秒", example = "12")
    private long latencyMs;

    @Schema(description = "失败原因")
    private String reason;

    @Schema(description = "时间")
    private LocalDateTime timestamp;
}
