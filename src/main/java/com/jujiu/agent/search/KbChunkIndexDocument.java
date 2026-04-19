package com.jujiu.agent.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库分块索引文档。
 *
 * <p>用于将文档分块、元数据和向量信息统一写入 Elasticsearch，
 * 支撑后续向量检索、关键词检索和混合检索能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/10
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
     * 所属章节标题，辅助检索和展示上下文信息。
     */
    @Schema(description = "所属章节标题", example = "第1章")
    private String sectionTitle;


    /**
     * 标签列表。
     */
    @Schema(description = "标签列表")
    private List<String> tags;

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
     * 分块向量。
     */
    @Schema(description = "分块向量")
    private List<Float> vector;
    
    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private String createdAt;
}
