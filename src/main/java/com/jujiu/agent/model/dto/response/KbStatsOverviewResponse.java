package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库概览统计响应")
public class KbStatsOverviewResponse {
    @Schema(description = "文档总数")
    private Long totalDocuments;
    @Schema(description = "成功文档数")
    private Long successDocuments;
    @Schema(description = "处理中文档数")
    private Long processingDocuments;
    @Schema(description = "失败文档数")
    private Long failedDocuments;

    @Schema(description = "查询总数")
    private Long totalQueries;
    @Schema(description = "成功查询数")
    private Long successQueries;
    @Schema(description = "反馈总数")
    private Long totalFeedbacks;

    @Schema(description = "近7天新增文档数")
    private Long documentsLast7Days;
    @Schema(description = "近30天新增文档数")
    private Long documentsLast30Days;
    @Schema(description = "近7天查询数")
    private Long queriesLast7Days;
    @Schema(description = "近30天查询数")
    private Long queriesLast30Days;

    @Schema(description = "helpful 数量")
    private Long helpfulCount;
    @Schema(description = "unhelpful 数量")
    private Long unhelpfulCount;
    @Schema(description = "平均评分")
    private Double avgRating;

    @Schema(description = "近30天查询趋势")
    private List<KbTrendPointResponse> queryTrend30Days;

    @Schema(description = "近30天文档趋势")
    private List<KbTrendPointResponse> documentTrend30Days;
}
