package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/20 15:05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbIndexFailureDetailResponse {
    @Schema(description = "文档ID")
    private Long documentId;
    
    @Schema(description = "失败分类", example = "ACL / DOCUMENT / CHUNK / EMBEDDING / ES / UNKNOWN")
    private String category;
    
    @Schema(description = "失败码", example = "EMBEDDING_REMOTE_TIMEOUT / ES_DELETE_FAILED ...")
    private String code;  
    
    @Schema(description = "索引阶段", example = "VALIDATE / EMBED / INDEX / REBUILD / REPAIR")
    private String stage;
    
    @Schema(description = "是否可重试", example = "true")
    private Boolean retriable;
    
    @Schema(description = "失败消息", example = "索引任务执行失败")
    private String message;
}

