package com.jujiu.agent.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库分块索引文档对象。
 *
 * <p>用于承接写入 Elasticsearch 的知识库分块数据。
 * 当前版本先服务文本检索，后续可扩展向量字段。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "KbChunkIndexDocument", description = "知识库分块索引文档")
public class KbChunkIndexDocument {
    /**
     * 分块 ID。
     */
    @Schema(description = "分块 ID",example = "123")
    private Long chunkId;


    /**
     * 文档 ID。
     */
    @Schema(description = "文档 ID",example = "3")
    private Long documentId;
    
    /**
     * 知识库 ID。
     */
    @Schema(description = "知识库ID", example = "1")
    private Long kbId;
    
    /**
     * 文档标题，用于检索和展示。
     */
    @Schema(description = "文档标题", example = "测试文档.md")
    private String title;
    
    /**
     * 分块正文，全文检索与答案生成的主要依据。
     */
    @Schema(description = "分块正文内容")
    private String content;

    /**
     * 文档所属用户 ID。
     */
    @Schema(description = "所属用户ID", example = "2037817206280290306")
    private Long ownerUserId;

    /**
     * 可见性。
     */
    @Schema(description = "可见性", example = "PRIVATE")
    private String visibility;

    /**
     * 是否启用。
     */
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private String createdAt;
}
