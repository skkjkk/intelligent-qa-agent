package com.jujiu.agent.service.kb.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.search.KbChunkIndexDocument;
import com.jujiu.agent.service.kb.ElasticsearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 索引服务实现。
 *
 * <p>负责知识库分块索引的创建、写入与删除。
 *
 * <p>当前版本为第一版文本检索实现：
 * <ul>
 *     <li>自动创建索引</li>
 *     <li>写入分块文本字段</li>
 *     <li>按文档 ID 删除索引数据</li>
 * </ul>
 *
 * <p>后续可在该类基础上扩展：
 * <ul>
 *     <li>dense_vector 向量字段</li>
 *     <li>混合检索支持</li>
 *     <li>索引版本升级</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/8
 */
@Service
@Slf4j
public class ElasticsearchIndexServiceImpl implements ElasticsearchIndexService {
    
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    private final ElasticsearchClient elasticsearchClient;
    
    public ElasticsearchIndexServiceImpl(KnowledgeBaseProperties knowledgeBaseProperties, ElasticsearchClient elasticsearchClient) {
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 确保知识库分块索引存在。
     */
    @Override
    public void ensureIndexExists() {
        // 获取知识库分块索引名称
        String indexName = getIndexName();

        log.info("[KB][ES] 确保索引存在 - indexName={}", indexName);
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(request -> request.index(indexName))
                    .value();
            
            if (exists) {
                log.info("[KB][ES] 索引已存在，indexName={}", indexName);
                return;
            }

            Map<String, Property> properties = buildIndexProperties();

            elasticsearchClient
                    .indices()
                    .create(request -> request
                            .index(indexName)
                            .mappings(mapping -> mapping
                                    .properties(properties))
                    );
            
            log.info("[KB][ES] 索引创建成功，indexName={}", indexName);
            
        } catch (IOException e) {
            
            log.error("[KB][ES] 创建索引失败，indexName={}", indexName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "创建 Elasticsearch 索引失败" + e.getMessage());
        }
    }

    /**
     * 写入单个分块索引。
     *
     * @param document 文档信息
     * @param chunk 分块信息
     * @param vector 分块向量，当前版本暂不使用
     */
    @Override
    public void indexChunk(KbDocument document, KbChunk chunk, float[] vector) {
        validateIndexChunk(document, chunk);

        ensureIndexExists();
        String indexName = getIndexName();
        String documentId = buildEsDocumentId(chunk.getId());

        KbChunkIndexDocument indexDocument = KbChunkIndexDocument.builder()
                .chunkId(chunk.getId())
                .documentId(document.getId())
                .kbId(document.getKbId())
                .title(document.getTitle())
                .content(chunk.getContent())
                .ownerUserId(document.getOwnerUserId())
                .visibility(document.getVisibility())
                .enabled(chunk.getEnabled() != null && chunk.getEnabled() == 1)
                .createdAt((chunk.getCreatedAt() != null ? chunk.getCreatedAt() : LocalDateTime.now()).toString())
                .build();

        try {
           elasticsearchClient.index(request -> request
                   .index(indexName)
                   .id(documentId)
                   .document(indexDocument)
           );
            log.info("[KB][ES] 分块索引写入成功，indexName={}, chunkId={}, documentId={}",
                    indexName, chunk.getId(), document.getId());
        } catch (IOException e) {
            log.error("[KB][ES] 分块索引写入失败，indexName={}, chunkId={}, documentId={}",
                    indexName, chunk.getId(), document.getId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "写入 Elasticsearch 分块索引失败");
        }
            }

    /**
     * 按文档 ID 删除索引。
     *
     * @param documentId 文档 ID
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "documentId 不能为空");
        }
        String indexName = getIndexName();

        try {
            elasticsearchClient.deleteByQuery(request -> request
                    .index(indexName)
                    .query(query -> query
                            .term(term -> term
                                    .field("documentId")
                                    .value(documentId)
                            )
                    )
            );
            log.info("[KB][ES] 按文档删除索引成功，indexName={}, documentId={}", indexName, documentId);
        } catch (IOException e) {
            log.error("[KB][ES] 按文档删除索引失败，indexName={}, documentId={}", indexName, documentId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "按文档删除 Elasticsearch 索引失败");
        }
    }

    /**
     * 构建索引字段定义。
     *
     * @return 索引字段映射
     */
    private Map<String, Property> buildIndexProperties() {
        Map<String, Property> properties = new HashMap<>();
        
        properties.put("chunkId", Property.of(p -> p.long_(l -> l)));
        properties.put("documentId", Property.of(p -> p.long_(l -> l)));
        properties.put("kbId", Property.of(p -> p.long_(l -> l)));
        properties.put("title", Property.of(p -> p.text(t -> t)));
        properties.put("content", Property.of(p -> p.text(t -> t)));
        properties.put("ownerUserId", Property.of(p -> p.long_(l -> l)));
        properties.put("visibility", Property.of(p -> p.keyword(k -> k)));
        properties.put("enabled", Property.of(p -> p.boolean_(b -> b)));
        properties.put("createdAt", Property.of(p -> p.date(d -> d)));
        
        return properties;
    }

    /**
     * 校验索引写入参数。
     *
     * @param document 文档信息
     * @param chunk 分块信息
     * @param vector 分块向量
     */
    private void validateIndexChunk(KbDocument document, KbChunk chunk) {
        if (document == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "document 不能为空");
        }

        if (chunk == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "chunk 不能为空");
        }
        
        if (document.getId() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "documentId 不能为空");
        }
        
        if (chunk.getId() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "chunkId 不能为空");
        }
        
        if (chunk.getContent() == null || chunk.getContent().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "chunk content 不能为空");
        }
    }
    
    /**
     * 获取知识库分块索引名称。
     *
     * @return 索引名称
     */
    private String getIndexName() {
        String indexName = knowledgeBaseProperties.getElasticsearch().getIndexName();
        return (indexName == null || indexName.isBlank()) ? "kb_chunks_v1" : indexName;
    }

    /**
     * 构建 Elasticsearch 文档 ID。
     *
     * @param chunkId 分块 ID
     * @return ES 文档 ID
     */
    private String buildEsDocumentId(Long chunkId) {
        return "chunk_" + chunkId;
    }
}
