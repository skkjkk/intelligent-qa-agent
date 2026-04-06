package com.jujiu.agent.search;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch 分块索引文档。
 *
 * <p>映射到索引 {@code kb_chunks_v1}，用于向量检索（kNN）
 * 与 BM25 全文检索。
 *
 * <p>向量维度固定为 2048，相似度算法为 cosine。
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 10:06
 */
@Data
public class KbChunkIndexDocument {

    /** 对应 {@code kb_chunk.id}。 */
    private Long chunkId;


    /** 对应 {@code kb_document.id}。 */
    private Long documentId;
    
    /** 知识库 ID。 */
    private Long kbId;
    
    /** 文档标题，用于检索和展示。 */
    private String title;
    
    /** 分块正文，全文检索与答案生成的主要依据。 */
    private String content;

    /** 所属章节标题。 */
    private String sectionTitle;
    
    /** 标签列表。 */
    private List<String> tags;

    /** 文档所有者 ID，用于 ACL 过滤。 */
    private Long ownerUserId;
    
    /** 可见性。 */
    private String visibility;
    
    /** 是否启用。 */
    private Boolean enabled;
    
    /** 稠密向量，维度 2048。 */
    private float[] vector;

    /** 创建时间。 */
    private LocalDateTime createdAt;

}
