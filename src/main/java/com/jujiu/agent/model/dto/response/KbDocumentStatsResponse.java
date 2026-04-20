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

    @Schema(description = "按文件类型分布")
    private List<KbDimensionCountResponse> fileTypeDistribution;
    
    @Schema(description = "按状态分布")
    private List<KbDimensionCountResponse> statusDistribution;

    @Schema(description = "近7天文档趋势")
    private List<KbTrendPointResponse> trend7Days;
    
    @Schema(description = "近30天文档趋势")
    private List<KbTrendPointResponse> trend30Days;
}
