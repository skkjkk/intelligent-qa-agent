package com.jujiu.agent.model.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库概览统计响应对象。
 *
 * <p>用于返回当前用户在指定知识库下的文档、查询与反馈等概览统计信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库概览统计响应")
public class KbStatsOverviewResponse {
    /**
     * 文档总数。
     */
    @Schema(description = "文档总数")
    private Long totalDocuments;

    /**
     * 成功文档数。
     */
    @Schema(description = "成功文档数")
    private Long successDocuments;

    /**
     * 处理中文档数。
     */
    @Schema(description = "处理中文档数")
    private Long processingDocuments;

    /**
     * 失败文档数。
     */
    @Schema(description = "失败文档数")
    private Long failedDocuments;

    /**
     * 查询总数。
     */
    @Schema(description = "查询总数")
    private Long totalQueries;

    /**
     * 成功查询数。
     */
    @Schema(description = "成功查询数")
    private Long successQueries;

    /**
     * 反馈总数。
     */
    @Schema(description = "反馈总数")
    private Long totalFeedbacks;
}
