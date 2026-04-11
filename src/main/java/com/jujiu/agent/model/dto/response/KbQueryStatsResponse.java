package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库问答统计响应对象。
 *
 * <p>用于返回当前用户在指定知识库下的问答数量、
 * 状态分布、平均耗时与平均 Token 消耗等统计信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库问答统计响应")
public class KbQueryStatsResponse {
    @Schema(description = "查询总数")
    private Long totalQueries;

    @Schema(description = "成功查询数")
    private Long successQueries;

    @Schema(description = "空结果查询数")
    private Long emptyQueries;

    @Schema(description = "失败查询数")
    private Long failedQueries;

    @Schema(description = "平均耗时，单位毫秒")
    private Long avgLatencyMs;

    @Schema(description = "平均总Token数")
    private Long avgTotalTokens;
}
