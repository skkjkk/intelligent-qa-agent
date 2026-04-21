package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
/**
 * 知识库问答统计响应对象
 * 包含知识库问答的统计信息，如查询总数、成功查询数、空结果查询数、失败查询数、平均耗时、平均总Token数、helpful 数量、unhelpful 数量、平均评分、评分分布、近7天查询趋势、近30天查询趋势等。
 * 该对象用于展示知识库问答的统计信息，帮助用户了解问答库的运行情况和性能。
 * @author jujiu
 * @date 2026/4/20
 * @version 1.0.0
 * @since 1.0.0
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

    @Schema(description = "helpful 数量")
    private Long helpfulCount;
    @Schema(description = "unhelpful 数量")
    private Long unhelpfulCount;
    @Schema(description = "平均评分")
    private Double avgRating;
    @Schema(description = "评分分布")
    private List<KbDimensionCountResponse> ratingDistribution;

    @Schema(description = "近7天查询趋势")
    private List<KbTrendPointResponse> trend7Days;
    @Schema(description = "近30天查询趋势")
    private List<KbTrendPointResponse> trend30Days;
}
