package com.jujiu.agent.module.kb.application.service;

/**
 * 向量化服务接口。
 *
 * <p>负责将查询文本或文档分块文本转换为向量表示，
 * 供后续 Elasticsearch 索引写入和向量检索使用。
 *
 * <p>该服务只负责文本向量化，不负责：
 * <ul>
 *     <li>检索逻辑</li>
 *     <li>索引写入</li>
 *     <li>RAG 问答编排</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
public interface EmbeddingService {
    /**
     * 对用户查询文本进行向量化。
     *
     * @param text 查询文本
     * @return 向量结果
     */
    float[] embedQuery(String text);

    /**
     * 对文档分块内容进行向量化。
     *
     * @param text 文档文本
     * @return 向量结果
     */
    float[] embedDocument(String text);
}
