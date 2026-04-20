package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库批量操作响应对象。
 *
 * <p>用于返回批量索引、批量重建等操作的执行摘要信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库批量操作响应")
public class KbBatchOperationResponse {

    @Schema(description = "总处理数量")
    private Integer totalCount;

    @Schema(description = "成功数量")
    private Integer successCount;

    @Schema(description = "失败数量")
    private Integer failedCount;

    @Schema(description = "失败文档ID列表")
    private List<Long> failedDocumentIds;

    @Schema(description = "失败明细")
    private List<KbIndexFailureDetailResponse> failedDetails;

    @Schema(description = "失败分类汇总")
    private List<KbDimensionCountResponse> failedCategorySummary;
    
    @Schema(description = "说明信息")
    private String message;
}
