package com.jujiu.agent.service.kb.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/16 21:31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "检索候选结果")
public class RetrievalCandidate {
    
    /**
     * 分片ID
     */
    @Schema(description = "分片ID")
    private Long chunkId;
    
    /**
     * 文档ID
     */
    @Schema(description = "文档ID")
    private Long documentId;
    
    /**
     * 文档标题
     */
    @Schema(description = "文档标题")
    private String documentTitle;
    
    /**
     * 分片内容
     */
    @Schema(description = "分片内容")
    private String content;
    
    /**
     * 分数
     */
    @Schema(description = "分数")
    private Double score;
    
    /**
     * 分片索引
     */
    @Schema(description = "分片索引")   
    private Integer chunkIndex;
    
    /**
     * 源类型
     */
    @Schema(description = "源类型", example = "VECTOR / KEYWORD / HYBRID")
    private String sourceType;
    
    /**
     * 排名
     */
    @Schema(description = "排名")
    private Integer rank;
}
