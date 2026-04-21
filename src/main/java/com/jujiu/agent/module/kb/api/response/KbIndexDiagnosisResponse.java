package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/20 15:07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbIndexDiagnosisResponse {
    @Schema(description = "文档ID")
    private Long documentId;
    
    @Schema(description = "解析状态", example = "SUCCESS / FAILED")
    private String parseStatus;
    
    @Schema(description = "索引状态", example = "SUCCESS / FAILED")
    private String indexStatus;
    
    @Schema(description = "数据库分块数量", example = "1000")
    private Integer dbChunkCount;
    
    @Schema(description = "活跃分块数量", example = "500")
    private Integer activeChunkCount;
    
    @Schema(description = "ES分块数量", example = "1000")
    private Long esChunkCount;
    
    @Schema(description = "是否一致", example = "true")
    private Boolean consistent;
    
    @Schema(description = "异常列表", example = "INDEX_STATUS_FAILED_BUT_ES_EXISTS")
    private List<String> anomalies; // 例如: INDEX_STATUS_FAILED_BUT_ES_EXISTS
    
    @Schema(description = "修复操作", example = "REINDEX / MARK_SUCCESS / MARK_FAILED / NONE")
    private String repairAction;    // REINDEX / MARK_SUCCESS / MARK_FAILED / NONE
}
