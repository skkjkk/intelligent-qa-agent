package com.jujiu.agent.module.kb.application.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.domain.entity.KbChunk;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.infrastructure.search.KbChunkIndexDocument;
import com.jujiu.agent.module.kb.application.service.ElasticsearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    /** 知识库配置属性。 */
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    /** Elasticsearch 客户端。 */
    private final ElasticsearchClient elasticsearchClient;
    
    /**
     * 构造方法。
     *
     * @param knowledgeBaseProperties 知识库配置属性
     * @param elasticsearchClient     Elasticsearch 客户端
     */
    public ElasticsearchIndexServiceImpl(KnowledgeBaseProperties knowledgeBaseProperties, ElasticsearchClient elasticsearchClient) {
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 确保知识库分块索引存在。
     */
    @Override
    public void ensureIndexExists() {
        // 1. 获取知识库分块索引名称
        String indexName = getIndexName();

        log.info("[KB][ES] 确保索引存在 - indexName={}", indexName);
        try {
            // 2. 检查索引是否已存在
            boolean exists = elasticsearchClient
                    .indices()
                    .exists(request -> request.index(indexName))
                    .value();
            
            // 3. 如果索引已存在，则直接返回
            if (exists) {
                log.info("[KB][ES] 索引已存在，indexName={}", indexName);
                return;
            }

            // 4. 索引不存在，构建字段映射并创建索引
            log.info("[KB][ES] 索引不存在，创建索引，indexName={}", indexName);
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
        // 1. 校验写入参数是否合法
        validateIndexChunk(document, chunk, vector);
        
        // 2. 确保目标索引已创建
        ensureIndexExists();
        
        // 3. 获取索引名称并构建 ES 文档 ID
        String indexName = getIndexName();
        String documentId = buildEsDocumentId(chunk.getId());

        // 4. 构建索引文档对象
        KbChunkIndexDocument indexDocument = KbChunkIndexDocument.builder()
                .chunkId(chunk.getId())
                .documentId(document.getId())
                .kbId(document.getKbId())
                .title(document.getTitle())
                .content(chunk.getContent())
                .sectionTitle(chunk.getSectionTitle())
                .tags(List.of())
                .ownerUserId(document.getOwnerUserId())
                .visibility(document.getVisibility())
                .enabled(chunk.getEnabled() != null && chunk.getEnabled() == 1)
                .vector(convertVector(vector))
                .createdAt((chunk.getCreatedAt() != null ? chunk.getCreatedAt() : LocalDateTime.now()).toString())
                .build();

        try {
            // 5. 调用 Elasticsearch 客户端写入索引文档
            elasticsearchClient
                    .index(request -> request
                            .index(indexName)
                            .id(documentId)
                            .document(indexDocument)
                    );

            log.info("[KB][ES] 分块索引写入成功 - indexName={}, chunkId={}, documentId={}, vectorDimension={}",
                    indexName, chunk.getId(), document.getId(), vector.length);
        } catch (IOException e) {
            log.error("[KB][ES] 分块索引写入失败 - indexName={}, chunkId={}, documentId={}",
                    indexName, chunk.getId(), document.getId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "写入 Elasticsearch 分块索引失败: " + e.getMessage());
        }
    }

    /**
     * 将基础类型数组转换为 Elasticsearch 可序列化的向量列表。
     *
     * @param vector 原始向量数组
     * @return 向量列表
     */
    private List<Float> convertVector(float[] vector) {
        // 1. 初始化结果列表
        List<Float> result = new ArrayList<>();
        
        // 2. 若向量为空，直接返回空列表
        if (vector == null) {
            return result;
        }
        
        // 3. 将 float 数组逐个转换为 Float 列表
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    /**
     * 按文档 ID 删除索引。
     *
     * @param documentId 文档 ID
     */
    @Override
    public void deleteByDocumentId(Long documentId) {
        // 1. 参数校验
        if (documentId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "documentId 不能为空");
        }
        
        // 2. 获取索引名称
        String indexName = getIndexName();

        try {
            // 3. 构造 term 查询并按 documentId 删除匹配的索引文档
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

    @Override
    public Long countByDocumentId(Long documentId) {
        if (documentId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "documentId 不能为空");
        }
        String indexName = getIndexName();
        try {
            return elasticsearchClient.count(request -> request
                    .index(indexName)
                    .query(query -> query
                            .term(term -> term
                                    .field("documentId")
                                    .value(documentId)
                            )
                    )
            ).count();
        } catch (IOException e) {
            log.error("[KB][ES] 统计文档索引数量失败，indexName={}, documentId={}", indexName, documentId, e);
            throw new BusinessException(ResultCode.ES_INDEX_COUNT_FAILED, "统计 Elasticsearch 文档索引数量失败");
        }
    }

    @Override
    public void deleteByDocumentIdAndExcludeChunkIds(Long documentId, List<Long> keepChunkIds) {
        if (documentId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "documentId 不能为空");
        }
        if (keepChunkIds == null || keepChunkIds.isEmpty()) {
            deleteByDocumentId(documentId);
            return;
        }

        String indexName = getIndexName();
        try {
            List<FieldValue> keepValues = keepChunkIds.stream().map(FieldValue::of).toList();
            elasticsearchClient.deleteByQuery(request -> request
                    .index(indexName)
                    .query(query -> query
                            .bool(bool -> bool
                                    .must(must -> must.term(term -> term.field("documentId").value(documentId)))
                                    .mustNot(mustNot -> mustNot.terms(terms -> terms
                                            .field("chunkId")
                                            .terms(values -> values.value(keepValues))
                                    ))
                            )
                    )
            );
            log.info("[KB][ES] 删除旧分块索引完成 - indexName={}, documentId={}, keepChunkCount={}",
                    indexName, documentId, keepChunkIds.size());
        } catch (IOException e) {
            log.error("[KB][ES] 删除旧分块索引失败，indexName={}, documentId={}", indexName, documentId, e);
            throw new BusinessException(ResultCode.ES_INDEX_DELETE_FAILED, "删除旧分块索引失败");
        }
    }

    /**
     * 构建索引字段定义。
     *
     * @return 索引字段映射
     */
    private Map<String, Property> buildIndexProperties() {
        // 1. 初始化字段映射容器
        Map<String, Property> properties = new HashMap<>();
        
        // 2. 注册基础 ID 与关系字段
        properties.put("chunkId", Property.of(p -> p.long_(l -> l)));
        properties.put("documentId", Property.of(p -> p.long_(l -> l)));
        properties.put("kbId", Property.of(p -> p.long_(l -> l)));

        // 3. 注册文本检索字段。
        // title 和 sectionTitle 同时保留 text + keyword，
        // 便于后续同时支持：
        // 1）BM25 全文检索
        // 2）术语型精确匹配
        properties.put("title", Property.of(p -> p.text(t -> t
                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
        )));

        properties.put("content", Property.of(p -> p.text(t -> t)));

        properties.put("sectionTitle", Property.of(p -> p.text(t -> t
                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
        )));
        
        // 4. 注册标签与权限字段
        properties.put("tags", Property.of(p -> p.keyword(k -> k)));
        properties.put("ownerUserId", Property.of(p -> p.long_(l -> l)));
        properties.put("visibility", Property.of(p -> p.keyword(k -> k)));
        properties.put("enabled", Property.of(p -> p.boolean_(b -> b)));
        
        // 5. 注册稠密向量字段，用于语义检索
        properties.put("vector", Property.of(p -> p.denseVector(v -> v
                .dims(getVectorDimension())
                .index(true)
                .similarity("cosine"))));
        
        // 6. 注册时间字段
        properties.put("createdAt", Property.of(p -> p.date(d -> d)));
        
        return properties;
    }

    /**
     * 获取向量维度配置。
     *
     * @return 向量维度
     */
    private Integer getVectorDimension() {
        // 1. 从配置中读取向量维度
        Integer dimension = knowledgeBaseProperties.getEmbedding().getDimension();
        
        // 2. 若配置非法，返回默认维度 2048
        return (dimension == null || dimension <= 0) ? 2048 : dimension;
    }

    /**
     * 校验索引写入参数。
     *
     * @param document 文档信息
     * @param chunk 分块信息
     * @param vector 分块向量
     */
    private void validateIndexChunk(KbDocument document, KbChunk chunk, float[] vector) {
        // 1. 校验文档对象不为空
        if (document == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "document 不能为空");
        }

        // 2. 校验分块对象不为空
        if (chunk == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "chunk 不能为空");
        }

        // 3. 校验向量数组不为空且长度大于 0
        if (vector == null || vector.length == 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "vector 不能为空");
        }
        
        // 4. 校验文档 ID 不为空
        if (document.getId() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "documentId 不能为空");
        }
        
        // 5. 校验分块 ID 不为空
        if (chunk.getId() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "chunkId 不能为空");
        }
        
        // 6. 校验分块内容不为空
        if (chunk.getContent() == null || chunk.getContent().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "chunk content 不能为空");
        }

        // 7. 校验向量维度是否与配置一致（不一致时仅记录警告，不阻断写入）
        Integer expectedDimension = getVectorDimension();
        if (vector.length != expectedDimension) {
            log.warn("[KB][ES] 向量维度与配置不一致 - chunkId={}, expectedDimension={}, actualDimension={}",
                    chunk.getId(), expectedDimension, vector.length);
        }
    }
    
    /**
     * 获取知识库分块索引名称。
     *
     * @return 索引名称
     */
    private String getIndexName() {
        // 1. 从配置中读取索引名称
        String indexName = knowledgeBaseProperties.getElasticsearch().getIndexName();
        
        // 2. 若配置为空，返回默认索引名 kb_chunks_v1
        return (indexName == null || indexName.isBlank()) ? "kb_chunks_v1" : indexName;
    }

    /**
     * 构建 Elasticsearch 文档 ID。
     *
     * @param chunkId 分块 ID
     * @return ES 文档 ID
     */
    private String buildEsDocumentId(Long chunkId) {
        // 使用固定前缀拼接分块 ID，确保 ES 文档 ID 的唯一性和可读性
        return "chunk_" + chunkId;
    }
}
