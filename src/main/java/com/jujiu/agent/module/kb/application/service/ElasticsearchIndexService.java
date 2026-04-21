package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.domain.entity.KbChunk;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;

import java.util.List;

/**
 * Elasticsearch 索引服务接口。
 *
 * <p>负责知识库分块数据的索引写入与删除，
 * 是文档分块数据进入检索体系的统一入口。
 *
 * <p>该服务只负责索引读写，不负责：
 * <ul>
 *     <li>文本向量化</li>
 *     <li>检索排序</li>
 *     <li>RAG 问答生成</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
public interface ElasticsearchIndexService {
    /**
     * 写入单个文档分块索引。
     *
     * @param document 文档信息
     * @param chunk 分块信息
     * @param vector 分块向量
     */
    void indexChunk(KbDocument document, KbChunk chunk, float[] vector);

    /**
     * 按文档 ID 删除对应的全部分块索引。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 确保知识库分块索引存在。
     */
    void ensureIndexExists();

    /**
     * 统计指定文档 ID 下的分块索引数量。
     *
     * @param documentId 文档 ID
     * @return 分块索引数量
     */
    Long countByDocumentId(Long documentId);

    /**
     * 删除指定文档 ID 下的分块索引，排除指定分块 ID。
     *
     * @param documentId 文档 ID
     * @param keepChunkIds 要保留的分块 ID 列表
     */
    void deleteByDocumentIdAndExcludeChunkIds(Long documentId, List<Long> keepChunkIds);

}
