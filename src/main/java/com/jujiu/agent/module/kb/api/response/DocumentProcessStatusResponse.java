package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/2 11:14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档处理状态响应参数", title = "文档处理状态响应参数")
public class DocumentProcessStatusResponse {
    @Schema(description = "文档ID", title = "文档ID")
    private Long documentId;

    @Schema(description = "处理状态", title = "处理状态")
    private String status;

    @Schema(description = "解析状态", title = "解析状态")
    private String parseStatus;

    @Schema(description = "索引状态", title = "索引状态")
    private String indexStatus;

    @Schema(description = "分块数量", title = "分块数量")
    private Integer chunkCount;
    
    @Schema(description = "更新时间", title = "更新时间")
    private LocalDateTime updatedAt;
}
