package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档统计响应对象。
 *
 * <p>用于返回当前用户在指定知识库下的文档状态分布、
 * 文件类型分布和分块数量等统计信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库文档统计响应对象")
public class KbDocumentStatsResponse {
    
    @Schema(description = "文档总数")
    private Long totalDocuments;

    @Schema(description = "成功文档数")
    private Long successDocuments;

    @Schema(description = "处理中文档数")
    private Long processingDocuments;

    @Schema(description = "失败文档数")
    private Long failedDocuments;

    @Schema(description = "PDF 文档数")
    private Long pdfDocuments;

    @Schema(description = "DOCX 文档数")
    private Long docxDocuments;

    @Schema(description = "Markdown 文档数")
    private Long mdDocuments;

    @Schema(description = "TXT 文档数")
    private Long txtDocuments;

    @Schema(description = "HTML 文档数")
    private Long htmlDocuments;

    @Schema(description = "分块总数")
    private Long totalChunks;
}
