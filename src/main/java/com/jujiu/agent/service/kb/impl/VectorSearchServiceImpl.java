package com.jujiu.agent.service.kb.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbChunkRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.search.KbChunkIndexDocument;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.EmbeddingService;
import com.jujiu.agent.service.kb.VectorSearchService;
import com.jujiu.agent.service.kb.model.SearchDebugResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.swing.text.Document;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库向量检索服务实现。
 *
 * <p>负责执行用户问题的检索流程，支持向量检索、关键词检索以及混合检索（加权融合），
 * 并基于文档 ACL 对结果进行权限过滤。
 *
 * <p>核心职责包括：
 * <ul>
 *     <li>问题向量化（调用 EmbeddingService）</li>
 *     <li>基于 Elasticsearch 的 KNN 向量检索</li>
 *     <li>基于数据库分块内容的关键词轻量匹配</li>
 *     <li>向量与关键词结果的加权融合与重排序</li>
 *     <li>检索参数校验、文档范围 ACL 过滤、索引名称解析等辅助逻辑</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Service
@Slf4j
public class VectorSearchServiceImpl implements VectorSearchService {
    
    /** 默认候选池大小。 */
    private static final int DEFAULT_CANDIDATE_TOP_K = 15;
    
    /** 默认最终候选池大小。 */
    private static final int DEFAULT_FINAL_CANDIDATE_TOP_K = 8;

    /** 正文完整 query 命中加分。 */
    private static final double CONTENT_EXACT_MATCH_SCORE = 12D;

    /** 标题完整 query 命中加分。 */
    private static final double TITLE_EXACT_MATCH_SCORE = 8D;

    /** 章节标题完整 query 命中加分。 */
    private static final double SECTION_TITLE_EXACT_MATCH_SCORE = 6D;

    /** 正文词项命中加分。 */
    private static final double CONTENT_TERM_MATCH_SCORE = 2D;

    /** 标题词项命中加分。 */
    private static final double TITLE_TERM_MATCH_SCORE = 2D;

    /** 章节标题词项命中加分。 */
    private static final double SECTION_TITLE_TERM_MATCH_SCORE = 1.5D;

    /** 过短 chunk 轻微降权值。 */
    private static final double SHORT_CHUNK_PENALTY = 0.5D;

    /** raw candidate 阶段单文档最多保留数量。 */
    private static final int MAX_RAW_CANDIDATES_PER_DOCUMENT = 4;

    /** 是否启用 BM25 候选召回。 */
    private static final boolean ENABLE_BM25_SEARCH = true;

    /** BM25 中 title 字段权重。 */
    private static final float BM25_TITLE_BOOST = 4.0F;

    /** BM25 中 sectionTitle 字段权重。 */
    private static final float BM25_SECTION_TITLE_BOOST = 2.5F;

    /** BM25 中 content 字段权重。 */
    private static final float BM25_CONTENT_BOOST = 1.0F;

    /** BM25 中 title 字段 phrase 匹配权重。 */
    private static final float BM25_TITLE_PHRASE_BOOST = 6.0F;

    /** BM25 中 sectionTitle 字段 phrase 匹配权重。 */
    private static final float BM25_SECTION_TITLE_PHRASE_BOOST = 4.0F;

    /** BM25 中 content 字段 phrase 匹配权重。 */
    private static final float BM25_CONTENT_PHRASE_BOOST = 2.5F;

    /** BM25 中 title 字段普通匹配权重。 */
    private static final float BM25_TITLE_MATCH_BOOST = 4.0F;

    /** BM25 中 sectionTitle 字段普通匹配权重。 */
    private static final float BM25_SECTION_TITLE_MATCH_BOOST = 2.5F;

    /** BM25 中 content 字段普通匹配权重。 */
    private static final float BM25_CONTENT_MATCH_BOOST = 1.0F;

    /** BM25 拆词补充匹配权重。 */
    private static final float BM25_TERM_MATCH_BOOST = 0.8F;

    /** 自然语言 query 下的向量权重。 */
    private static final double NATURAL_LANGUAGE_VECTOR_WEIGHT = 0.70D;

    /** 自然语言 query 下的 BM25 权重。 */
    private static final double NATURAL_LANGUAGE_BM25_WEIGHT = 0.50D;

    /** 术语型 query 下的向量权重。 */
    private static final double TERM_LIKE_VECTOR_WEIGHT = 0.45D;

    /** 术语型 query 下的 BM25 权重。 */
    private static final double TERM_LIKE_BM25_WEIGHT = 0.85D;


    /** 向量化服务，用于将用户问题转换为查询向量。 */
    private final EmbeddingService embeddingService;
    
    /** 知识库文档数据访问层。 */
    private final KbDocumentRepository kbDocumentRepository;
    
    /** 知识库分块数据访问层。 */
    private final KbChunkRepository kbChunkRepository;
    
    /** Elasticsearch 客户端，用于执行向量检索。 */
    private final ElasticsearchClient elasticsearchClient;
    
    /** 知识库配置属性，包含索引名称、检索参数等。 */
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    
    /** 文档 ACL 服务，用于过滤当前用户可读的文档范围。 */
    private final DocumentAclService documentAclService;
    
    /**
     * 构造方法。
     *
     * @param embeddingService         向量化服务
     * @param kbDocumentRepository     文档数据访问层
     * @param kbChunkRepository        分块数据访问层
     * @param elasticsearchClient      Elasticsearch 客户端
     * @param knowledgeBaseProperties  知识库配置属性
     * @param documentAclService       文档 ACL 服务
     */
    public VectorSearchServiceImpl(EmbeddingService embeddingService,
                                   KbDocumentRepository kbDocumentRepository,
                                   KbChunkRepository kbChunkRepository,
                                   ElasticsearchClient elasticsearchClient,
                                   KnowledgeBaseProperties knowledgeBaseProperties,
                                   DocumentAclService documentAclService) {
        this.embeddingService = embeddingService;
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbChunkRepository = kbChunkRepository;
        this.elasticsearchClient = elasticsearchClient;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.documentAclService = documentAclService;
    }


    /**
     * 执行知识库检索。
     *
     * <p>融合流程：先分别执行向量检索和关键词检索，再对两者结果做加权融合、
     * 排序截断并重新赋予排名，最终返回 topK 条分块结果。
     *
     * @param kbId     知识库 ID
     * @param userId   当前用户 ID
     * @param question 用户问题
     * @param topK     返回结果数量
     * @return 检索结果列表
     */
    @Override
    public List<ChunkSearchResult> search(Long kbId, Long userId, String question, Integer topK) {
        // 1. 校验检索参数合法性
        validateSearchParams(kbId, userId, question, topK);
        
        long startTime = System.currentTimeMillis();
        log.info("[KB][SEARCH] 开始执行检索 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, question.length());

        // 2. 加载当前用户在指定知识库下可读的文档列表
        List<KbDocument> documents = loadAvailableDocuments(kbId, userId);
        if (documents.isEmpty()) {
            log.info("[KB][SEARCH][ACL] 当前用户无可检索文档 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        // 3. 提取可读文档 ID 集合，用于后续检索范围过滤
        Set<Long> documentIds = documents.stream()
                .map(KbDocument::getId)
                .collect(Collectors.toSet());

        // 4. 将用户问题转换为查询向量
        float[] queryVector = embeddingService.embedQuery(question);

        // 5. 计算候选池大小和最终输出给 organizer 的 raw candidate 数量
        int candidateTopK = getCandidateTopK(topK);
        int finalCandidateTopK = getFinalCandidateTopK(topK);
        
        log.info("[KB][SEARCH][CANDIDATE] 检索候选池参数已确定 - kbId={}, userId={}, topK={}, candidateTopK={}, finalCandidateTopK={}",
                kbId, userId, topK, candidateTopK, finalCandidateTopK);

        // 6. 执行向量召回候选池检索，并再次按 ACL 文档范围做二次过滤。
        List<ChunkSearchResult> vectorResults = vectorSearch(
                kbId, 
                userId, 
                question, 
                queryVector, 
                candidateTopK, 
                documentIds
        );
        vectorResults = filterByDocumentScope(vectorResults, documentIds);

        // 7. 执行 BM25 候选召回，并按 ACL 文档范围做二次过滤。
        //  当前阶段保留旧 keywordSearch 实现作为回退，但主路径切到 BM25。
        List<ChunkSearchResult> keywordResults;
        if (ENABLE_BM25_SEARCH) {
            keywordResults = bm25Search(
                    kbId, 
                    userId, 
                    question, 
                    candidateTopK, 
                    documentIds
            );
        } else {
            keywordResults = keywordSearch(
                    kbId,
                    userId,
                    question,
                    candidateTopK,
                    documentIds,
                    documents
            );
        }
        keywordResults = filterByDocumentScope(keywordResults, documentIds);

        // 8. 将双路候选结果做融合排序，并输出给 organizer 使用的 raw candidates。
        List<ChunkSearchResult> mergedResults = mergeResults(
                vectorResults, 
                keywordResults, 
                finalCandidateTopK,
                question
        );
        
        // 9. 对融合后的 raw candidates 执行轻量文档平衡，避免单文档过度占位。
        List<ChunkSearchResult> balancedResults = balanceResultsByDocument(mergedResults);

        // 10. 记录检索耗时并返回结果
        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[KB][SEARCH] 检索完成 - kbId={}, userId={}, vectorResultCount={}, keywordResultCount={}, mergedCount={}, latencyMs={}",
                kbId, userId, vectorResults.size(), keywordResults.size(), mergedResults.size(), latencyMs);
        
        return balancedResults;
    }

    /**
     * 执行检索调试。
     *
     * <p>该方法不进入 LLM 生成阶段，只返回检索层中间态结果，
     * 便于观察向量召回、BM25 召回、融合与 balancing 的实际表现。
     *
     * @param kbId     知识库 ID
     * @param userId   当前用户 ID
     * @param question 用户问题
     * @param topK     最终目标 topK
     * @return 检索调试结果
     */
    @Override
    public SearchDebugResult debugSearch(Long kbId, Long userId, String question, Integer topK) {
        // 1. 校验参数。
        validateSearchParams(kbId, userId, question, topK);

        log.info("[KB][SEARCH][DEBUG] 开始执行检索调试 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, question.length());

        // 2. 加载 ACL 范围内文档。
        List<KbDocument> documents = loadAvailableDocuments(kbId, userId);
        if (documents.isEmpty()) {
            log.info("[KB][SEARCH][DEBUG][ACL] 当前用户无可检索文档 - kbId={}, userId={}", kbId, userId);
            return SearchDebugResult.builder()
                    .vectorCandidates(List.of())
                    .bm25Candidates(List.of())
                    .mergedCandidates(List.of())
                    .balancedCandidates(List.of())
                    .build();
        }

        Set<Long> documentIds = documents.stream()
                .map(KbDocument::getId)
                .collect(Collectors.toSet());

        // 3. 计算候选池大小。
        int candidateTopK = getCandidateTopK(topK);
        int finalCandidateTopK = getFinalCandidateTopK(topK);

        // 4. 向量候选。
        float[] queryVector = embeddingService.embedQuery(question);
        List<ChunkSearchResult> vectorResults = vectorSearch(
                kbId, userId, question, queryVector, candidateTopK, documentIds
        );
        vectorResults = filterByDocumentScope(vectorResults, documentIds);

        // 5. BM25 候选。
        List<ChunkSearchResult> bm25Results = bm25Search(
                kbId, userId, question, candidateTopK, documentIds
        );
        bm25Results = filterByDocumentScope(bm25Results, documentIds);

        // 6. 融合结果。
        List<ChunkSearchResult> mergedResults = mergeResults(
                vectorResults, 
                bm25Results, 
                finalCandidateTopK,
                question
        );

        // 7. 平衡后结果。
        List<ChunkSearchResult> balancedResults = balanceResultsByDocument(mergedResults);

        log.info("[KB][SEARCH][DEBUG] 检索调试完成 - kbId={}, userId={}, vectorCount={}, bm25Count={}, mergedCount={}, balancedCount={}",
                kbId, userId, vectorResults.size(), bm25Results.size(), mergedResults.size(), balancedResults.size());

        return SearchDebugResult.builder()
                .vectorCandidates(vectorResults)
                .bm25Candidates(bm25Results)
                .mergedCandidates(mergedResults)
                .balancedCandidates(balancedResults)
                .build();
    }

    /**
     * 合并向量检索和 BM25 检索结果。
     *
     * <p>当前版本已升级为轻量 query 类型感知融合：
     * <ul>
     *     <li>术语型 query：BM25 权重更高</li>
     *     <li>自然语言 query：向量权重更高</li>
     * </ul>
     *
     * @param vectorResults  向量检索结果
     * @param keywordResults BM25 检索结果
     * @param topK           返回数量
     * @param question       用户问题
     * @return 合并后的检索结果
     */
    private List<ChunkSearchResult> mergeResults(List<ChunkSearchResult> vectorResults, 
                                                 List<ChunkSearchResult> keywordResults,
                                                 Integer topK,
                                                 String question) {

        RetrievalWeights weights = resolveRetrievalWeights(question);

        log.info("[KB][SEARCH][MERGE] 开始融合检索结果 - vectorCount={}, keywordCount={}, topK={}, vectorWeight={}, bm25Weight={}",
                vectorResults == null ? 0 : vectorResults.size(),
                keywordResults == null ? 0 : keywordResults.size(),
                topK,
                weights.vectorWeight(),
                weights.bm25Weight());

        // 1. 初始化融合结果映射表
        Map<Long, ChunkSearchResult> mergedMap = new LinkedHashMap<>();
        
        // 2. 将向量检索结果按权重 合并到映射表
        mergeInToMap(mergedMap, vectorResults, weights.vectorWeight());
        
        // 3. 将关键词检索结果按权重 合并到映射表
        mergeInToMap(mergedMap, keywordResults, weights.bm25Weight());

        // 4. 按融合得分降序排序，并截断至 topK
        List<ChunkSearchResult> mergedResults = mergedMap.values().stream()
                .sorted(Comparator.comparing(
                        ChunkSearchResult::getScore,
                        Comparator.nullsLast(Double::compareTo)
                ).reversed())
                .limit(topK)
                .toList();

        // 5. 为最终结果的每个元素重新赋排名
        for (int i = 0; i < mergedResults.size(); i++) {
            mergedResults.get(i).setRank(i + 1);
        }

        log.info("[KB][SEARCH][MERGE] 融合检索完成 - mergedCandidateCount={}, finalCount={}",
                mergedMap.size(), mergedResults.size());
        
        return mergedResults;
    }

    /**
     * 将检索结果按权重合并到目标映射中。
     *
     * <p>以分块 ID 为唯一键，若目标映射中已存在该分块则累加得分，
     * 否则新建结果对象放入映射。
     *
     * @param target  目标映射
     * @param source  来源结果
     * @param weight  当前来源权重
     */
    private void mergeInToMap(Map<Long, ChunkSearchResult> target, 
                              List<ChunkSearchResult> source, 
                              double weight) {
        // 1. 若来源结果为空，直接返回
        if (source == null || source.isEmpty()) {
            return;
        }

        // 2. 遍历来源结果，按 chunkId 聚合加权得分
        for (ChunkSearchResult result : source) {
            // 2.1 过滤无效结果
            if (result == null || result.getChunkId() == null) {
                continue;
            }

            // 2.2 计算当前来源的加权得分
            double weightedScore = (result.getScore() == null ? 0D : result.getScore()) * weight;
            ChunkSearchResult existing = target.get(result.getChunkId());
            
            // 2.3 若目标映射中不存在该分块，则新建并放入；否则累加得分
            if (existing == null) {
                target.put(result.getChunkId(), ChunkSearchResult.builder()
                        .chunkId(result.getChunkId())
                        .documentId(result.getDocumentId())
                        .documentTitle(result.getDocumentTitle())
                        .content(result.getContent())
                        .score(weightedScore)
                        .rank(0)
                        .build()
                );
            } else {
                double mergedScore = (existing.getScore() == null ? 0D : existing.getScore()) + weightedScore;
                existing.setScore(mergedScore);
            }
        }
    }

    /**
     * 执行关键词检索。
     *
     * <p>当前版本复用数据库分块与轻量文本匹配逻辑，
     * 加载候选分块后按问题关键词计算匹配得分，排序截断并返回 topK。
     *
     * @param kbId        知识库 ID
     * @param userId      当前用户 ID
     * @param question    用户问题
     * @param topK        返回数量
     * @param documentIds 可检索文档 ID 集合
     * @param documents   可用文档列表
     * @return 检索结果
     */
    private List<ChunkSearchResult> keywordSearch(Long kbId,
                                                  Long userId,
                                                  String question,
                                                  Integer topK,
                                                  Set<Long> documentIds,
                                                  List<KbDocument> documents) {
        // 1. 校验文档 ID 集合是否为空
        if (documentIds == null || documentIds.isEmpty()) {
            log.info("[KB][SEARCH][KEYWORD] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        log.info("[KB][SEARCH][KEYWORD] 开始关键词检索 - kbId={}, userId={}, topK={}, candidateDocumentCount={}",
                kbId, userId, topK, documentIds.size());

        // 2. 加载候选文档下的可用分块
        List<KbChunk> chunks = loadAvailableChunk(documentIds);
        if (chunks.isEmpty()) {
            log.info("[KB][SEARCH][KEYWORD] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        // 3. 基于问题与分块内容构建匹配结果并计算得分
        List<ChunkSearchResult> matchedResults = buildSearchResult(question, documents, chunks);

        // 4. 按得分降序排序并截断至 topK
        List<ChunkSearchResult> sortedResults = matchedResults.stream()
                .sorted(Comparator.comparing(
                                ChunkSearchResult::getScore,
                                Comparator.nullsLast(Double::compareTo)
                ).reversed())
                .limit(topK)
                .toList();
        
        // 5. 为结果重新赋排名
        for (int i = 0; i < sortedResults.size(); i++) {
            sortedResults.get(i).setRank(i + 1);
        }

        log.info("[KB][SEARCH][KEYWORD] 关键词检索完成 - kbId={}, userId={}, chunkCandidateCount={}, matchedCount={}, resultCount={}",
                kbId, userId, chunks.size(), matchedResults.size(), sortedResults.size());

        return sortedResults;
    }

    /**
     * 执行向量检索。
     *
     * <p>通过 Elasticsearch KNN 查询，基于查询向量检索与问题语义最相似的分块，
     * 同时按 kbId、enabled 状态及 documentIds 做布尔过滤，确保 ACL 合规。
     *
     * @param kbId         知识库 ID
     * @param userId       当前用户 ID
     * @param question     用户问题
     * @param queryVector  查询向量
     * @param topK         返回数量
     * @param documentIds  可检索文档 ID 集合
     * @return 检索结果
     */
    private List<ChunkSearchResult> vectorSearch(Long kbId, 
                                                 Long userId, 
                                                 String question, 
                                                 float[] queryVector, 
                                                 Integer topK, 
                                                 Set<Long> documentIds) {
        // 1. 校验查询向量是否为空
        if (queryVector == null || queryVector.length == 0) {
            log.warn("[KB][SEARCH][VECTOR] 查询向量为空 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }

        // 2. 校验文档 ID 集合是否为空
        if (documentIds == null || documentIds.isEmpty()) {
            log.info("[KB][SEARCH][VECTOR] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        // 3. 获取目标索引名称
        String indexName = getIndexName();
        log.info("[KB][SEARCH][VECTOR] 开始向量检索 - kbId={}, userId={}, topK={}, indexName={}, candidateDocumentCount={}, vectorDimension={}",
                kbId, userId, topK, indexName, documentIds.size(), queryVector.length);
        try {
            // 4. 将原始向量转换为 Elasticsearch 查询向量列表
            List<Float> vector = convertVector(queryVector);
            
            // 5. 执行 Elasticsearch KNN 向量检索，附加 ACL 与状态过滤条件
            var response = elasticsearchClient.search(search -> search
                            .index(indexName)
                            .size(topK)
                            .knn(knn -> knn
                                    .field("vector")
                                    .queryVector(vector)
                                    .k(topK)
                                    .numCandidates(Math.max(topK * 2, 20))
                                    .filter(filter -> filter
                                            .bool(bool -> bool
                                                    .must(m1 -> m1.term(t -> t.field("kbId").value(kbId)))
                                                    .must(m2 -> m2.term(t -> t.field("enabled").value(true)))
                                                    .must(m3 -> m3.terms(t -> t
                                                            .field("documentId")
                                                            .terms(v -> v.value(documentIds.stream()
                                                                    .map(FieldValue::of)
                                                                    .toList())))
                                                    )
                                            )
                                    )
                            ),
                    KbChunkIndexDocument.class
            );
            
            log.info("[KB][SEARCH][VECTOR][ACL] 向量检索文档范围已按 ACL 收口 - kbId={}, userId={}, documentCount={}",
                    kbId, userId, documentIds.size());

            // 6. 解析检索响应，构建结果列表
            List<ChunkSearchResult> results = new ArrayList<>();
            if (response.hits() == null || response.hits().hits().isEmpty()) {
                log.info("[KB][SEARCH][VECTOR] 向量检索无命中结果 - kbId={}, userId={}", kbId, userId);
                return results;
            }
            
            // 7. 遍历命中结果，转换为 ChunkSearchResult 并赋排名
            int rank = 1;
            for (var hit : response.hits().hits()) {
                KbChunkIndexDocument source = hit.source();
                if (source == null) {
                    continue;
                }
                
                results.add(ChunkSearchResult.builder()
                                .chunkId(source.getChunkId())
                                .documentId(source.getDocumentId())
                                .documentTitle(source.getTitle())
                                .content(source.getContent())
                                .score(hit.score() == null ? 0D : hit.score().doubleValue())
                                .rank(rank++)
                                .build()
                );
            }

            log.info("[KB][SEARCH][VECTOR] 向量检索完成 - kbId={}, userId={}, resultCount={}",
                    kbId, userId, results.size());
            
            return results;
        } catch (Exception e) {
            log.error("[KB][SEARCH][VECTOR] 向量检索失败 - kbId={}, userId={}, indexName={}",
                    kbId, userId, indexName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "向量检索失败");
        }
    }

    /**
     * 执行 BM25 候选召回。
     *
     * <p>当前实现使用 Elasticsearch 全文检索能力，在以下字段上执行 BM25 匹配：
     * <ul>
     *     <li>title</li>
     *     <li>sectionTitle</li>
     *     <li>content</li>
     * </ul>
     *
     * <p>字段权重遵循当前最终版方向：
     * <ul>
     *     <li>title 权重最高</li>
     *     <li>sectionTitle 次之</li>
     *     <li>content 为基础权重</li>
     * </ul>
     *
     * <p>该方法当前职责仅为“生成关键词候选池”，
     * 不负责最终证据整理，不负责 citation/context 生成。
     *
     * @param kbId        知识库 ID
     * @param userId      当前用户 ID
     * @param question    用户问题
     * @param topK        BM25 候选数量
     * @param documentIds 当前用户 ACL 范围内可检索文档 ID 集合
     * @return BM25 候选结果
     */
    private List<ChunkSearchResult> bm25Search(Long kbId, 
                                               Long userId, 
                                               String question, 
                                               Integer topK,
                                               Set<Long> documentIds) {
        // 1. ACL 文档范围为空时，直接返回空结果
        if (documentIds == null || documentIds.isEmpty()) {
            log.info("[KB][SEARCH][BM25] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }

        String indexName = getIndexName();

        log.info("[KB][SEARCH][BM25] 开始执行 BM25 候选召回 - kbId={}, userId={}, topK={}, indexName={}, documentCount={}, questionLength={}",
                kbId, userId, topK, indexName, documentIds.size(), question == null ? 0 : question.length());
        
        try{
            var response = elasticsearchClient.search(search -> search
                    .index(indexName)
                    .size(topK)
                    .query(query-> query
                            .bool(bool->bool
                                    // 1. 先收知识库范围，启用状态和ACL文档范围
                                    .filter(f1->f1.term(t->t.field("kbId").value(kbId)))
                                    .filter(f2->f2.term(t->t.field("enabled").value(true)))
                                    .filter(f3-> f3.terms(t->t
                                            .field("documentId")
                                            .terms(v->v.value(documentIds.stream()
                                                    .map(FieldValue::of)
                                                    .toList())
                                            )
                                    ))
                                    // 2. 再往BM25 should子句，允许title/sectionTitle/content匹配
                                    .must(m->m.bool(b->buildBm25ShouldClauses(b,question)))
                            )
                    ),
                    KbChunkIndexDocument.class
            );
            
            List<ChunkSearchResult> results = new ArrayList<>();
            if (response.hits() == null || response.hits().hits().isEmpty()) {
                log.info("[KB][SEARCH][BM25] BM25 候选召回无命中 - kbId={}, userId={}", kbId, userId);
                return results;
            }
            
            int rank = 1;
            for (var hit : response.hits().hits()) {
                KbChunkIndexDocument source = hit.source();
                if (source == null) {
                    continue;
                }

                results.add(ChunkSearchResult.builder()
                        .chunkId(source.getChunkId())
                        .documentId(source.getDocumentId())
                        .documentTitle(source.getTitle())
                        .content(source.getContent())
                        .score(hit.score() == null ? 0D : hit.score().doubleValue())
                        .rank(rank++)
                        .build()
                );
            }

            log.info("[KB][SEARCH][BM25] BM25 候选召回完成 - kbId={}, userId={}, resultCount={}, topChunkId={}, topDocumentId={}, topScore={}",
                    kbId,
                    userId,
                    results.size(),
                    results.isEmpty() ? null : results.get(0).getChunkId(),
                    results.isEmpty() ? null : results.get(0).getDocumentId(),
                    results.isEmpty() ? null : results.get(0).getScore());


            return results;

        } catch (Exception e) {
            log.error("[KB][SEARCH][BM25] BM25 候选召回失败 - kbId={}, userId={}, indexName={}",
                    kbId, userId, indexName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "BM25 检索失败");
        }
    }

    /**
     * 构建 BM25 第二版 should 子句。
     *
     * <p>第二版目标：
     * <ul>
     *     <li>让精确术语、接口名、路径、配置项更容易命中</li>
     *     <li>在不依赖 keyword 子字段的前提下，先通过 phrase + match 组合增强召回</li>
     *     <li>再通过拆词补充匹配，避免 query 稍有变化时完全失效</li>
     * </ul>
     *
     * <p>当前策略分三层：
     * <ol>
     *     <li>phrase 匹配：优先抓精确短语</li>
     *     <li>普通 match：保留 BM25 多字段召回能力</li>
     *     <li>term 拆词补充：避免精确短语不命中时完全无结果</li>
     * </ol>
     *
     * @param boolBuilder bool 查询构造器
     * @param question    用户问题
     * @return 构造后的 bool 查询
     */
    private Builder buildBm25ShouldClauses(Builder boolBuilder, String question) {

        // 1. query 为空时，不追加任何 BM25 匹配子句。
        if (question == null || question.isBlank()) {
            return boolBuilder;
        }

        String trimmedQuestion = question.trim();
        List<String> queryTerms = splitQuestionTerms(question);

        // 2. 第一层：phrase 匹配，优先支持精确术语和短语查询。
        boolBuilder
                .should(s1 -> s1.match(m -> m
                        .field("title")
                        .query(trimmedQuestion)
                        .boost(BM25_TITLE_PHRASE_BOOST))
                )
                .should(s2 -> s2.match(m -> m
                        .field("sectionTitle")
                        .query(trimmedQuestion)
                        .boost(BM25_SECTION_TITLE_PHRASE_BOOST))
                )
                .should(s3->s3.match(m->m
                        .field("content")
                        .query(trimmedQuestion)
                        .boost(BM25_CONTENT_PHRASE_BOOST))
                );

        // 3. 第二层：普通多字段 match，保留 BM25 主体全文召回能力。
        boolBuilder
                .should(s4 -> s4.match(m -> m
                        .field("title")
                        .query(trimmedQuestion)
                        .boost(BM25_TITLE_MATCH_BOOST)
                ))
                .should(s5 -> s5.match(m -> m
                        .field("sectionTitle")
                        .query(trimmedQuestion)
                        .boost(BM25_SECTION_TITLE_MATCH_BOOST)
                ))
                .should(s6 -> s6.match(m -> m
                        .field("content")
                        .query(trimmedQuestion)
                        .boost(BM25_CONTENT_MATCH_BOOST)
                ));

        // 4. 第三层：拆词补充匹配，避免 phrase / 整句 match 不命中时完全空结果。
        for (String term : queryTerms) {
            boolBuilder
                    .should(s -> s.match(m -> m
                            .field("title")
                            .query(term)
                            .boost(BM25_TERM_MATCH_BOOST)
                    ))
                    .should(s -> s.match(m -> m
                            .field("sectionTitle")
                            .query(term)
                            .boost(BM25_TERM_MATCH_BOOST)
                    ))
                    .should(s -> s.match(m -> m
                            .field("content")
                            .query(term)
                            .boost(BM25_TERM_MATCH_BOOST)
                    ));
        }
        
        // 5. 至少命中一个 should 子句。
        return boolBuilder.minimumShouldMatch("1");
    }
    
    
    /**
     * 基于问题和分块数据构造关键词检索结果。
     *
     * <p>当前实现会统一计算以下维度的匹配分数：
     * <ul>
     *     <li>完整 query 命中正文</li>
     *     <li>完整 query 命中标题</li>
     *     <li>完整 query 命中章节标题</li>
     *     <li>词项命中正文</li>
     *     <li>词项命中标题</li>
     *     <li>词项命中章节标题</li>
     * </ul>
     *
     * @param question  用户问题
     * @param documents 文档列表
     * @param chunks    分块列表
     * @return 检索结果列表
     */
    private List<ChunkSearchResult> buildSearchResult(String question, 
                                                      List<KbDocument> documents, 
                                                      List<KbChunk> chunks) {

        // 1. 初始化结果列表，并对问题做标准化和拆词预处理
        List<ChunkSearchResult> results = new ArrayList<>();
        String normalizedQuestion = normalize(question);
        List<String> questionTerms = splitQuestionTerms(question);

        // 2. 遍历所有候选分块，计算匹配得分并构建结果对象
        for (KbChunk chunk : chunks) {
            // 2.1 查找分块所属文档，找不到则跳过
            KbDocument document = findDocument(documents, chunk.getDocumentId());
            if (document == null) {
                continue;
            }

            // 2.2 计算当前分块的匹配得分
            Double score = calculateScore(normalizedQuestion, questionTerms, document, chunk);
            if (score <= 0) {
                continue;
            }

            // 2.3 构建检索结果对象并加入列表
            results.add(ChunkSearchResult.builder()
                    .chunkId(chunk.getId())
                    .documentId(chunk.getDocumentId())
                    .documentTitle(document.getTitle())
                    .content(chunk.getContent())
                    .score(score)
                    .rank(0)
                    .build()
            );
        }
        
        return results;
    }

    /**
     * 计算关键词检索分数。
     *
     * <p>当前版本采用固定维度评分结构，目标不是做复杂检索学习排序，
     * 而是提供一个稳定、可解释、可调优的第一版关键词候选打分模型。
     *
     * <p>评分维度包括：
     * <ul>
     *     <li>完整 query 命中正文</li>
     *     <li>完整 query 命中标题</li>
     *     <li>完整 query 命中章节标题</li>
     *     <li>词项命中正文</li>
     *     <li>词项命中标题</li>
     *     <li>词项命中章节标题</li>
     *     <li>过短 chunk 轻微降权</li>
     * </ul>
     *
     * @param normalizedQuestion 标准化后的问题
     * @param questionTerms      问题拆词结果
     * @param document           文档对象
     * @param chunk              分块对象
     * @return 关键词检索分数
     */
    private Double calculateScore(String normalizedQuestion, 
                                  List<String> questionTerms, 
                                  KbDocument document, 
                                  KbChunk chunk) {
        // 1. 对标题、章节标题和正文统一做标准化处理。
        String normalizedTitle = normalize(document.getTitle());
        String normalizedSectionTitle = normalize(chunk.getSectionTitle());
        String normalizedContent = normalize(chunk.getContent());
        
        double score = 0D;

        // 2. 完整 query 命中优先于散词命中，优先拉开分值层次。
        if (!normalizedQuestion.isBlank()) {
            if (normalizedContent.contains(normalizedQuestion)) {
                score += CONTENT_EXACT_MATCH_SCORE;
            }
            if (normalizedTitle.contains(normalizedQuestion)) {
                score += TITLE_EXACT_MATCH_SCORE;
            }
            if (normalizedSectionTitle.contains(normalizedQuestion)) {
                score += SECTION_TITLE_EXACT_MATCH_SCORE;
            }
        }

        // 3. 词项命中作为补充分值，不取代完整命中的价值。
        for (String term : questionTerms) {
            if (normalizedContent.contains(term)) {
                score += CONTENT_TERM_MATCH_SCORE;
            }
            if (normalizedTitle.contains(term)) {
                score += TITLE_TERM_MATCH_SCORE;
            }
            if (normalizedSectionTitle.contains(term)) {
                score += SECTION_TITLE_TERM_MATCH_SCORE;
            }
        }

        // 4. 内容过短的 chunk 一般证据承载能力较弱，做轻微降权，避免短噪声排太前。
        if (chunk.getCharCount() != null && chunk.getCharCount() < 20) {
            score -= SHORT_CHUNK_PENALTY;
        }

        // 5. 保证最终分数不小于 0。
        return Math.max(score, 0D);
    }
    
    /**
     * 查找指定分块所属文档。
     *
     * @param documents   文档列表
     * @param documentId  文档 ID
     * @return 文档对象，找不到时返回 {@code null}
     */
    private KbDocument findDocument(List<KbDocument> documents, Long documentId) {
        // 1. 遍历文档列表，按 ID 匹配
        for (KbDocument document : documents) {
            if (document.getId().equals(documentId)) {
                return document;
            }
        }
        // 2. 未找到匹配文档，返回 null
        return null;
    }

    /**
     * 将 query 拆分为适合 BM25 补充匹配的词项。
     *
     * <p>当前方法会尽量兼容以下 query 形态：
     * <ul>
     *     <li>普通中文自然语言</li>
     *     <li>camelCase：如 rebuildFailedIndexes</li>
     *     <li>下划线术语：如 knowledge_base</li>
     *     <li>路径术语：如 kb/query/stream</li>
     * </ul>
     *
     * @param question 用户问题
     * @return 拆词结果
     */
    private List<String> splitQuestionTerms(String question) {
        // 1. 若问题为空，直接返回空列表
        if (question == null || question.isEmpty()) {
            return List.of();
        }

        // 2. 先对 camelCase 做轻量拆分。
        String normalized = question
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace("_", " ")
                .replace("/", " ")
                .replace("-", " ")
                .replace("，", " ")
                .replace("。", " ")
                .replace("？", " ")
                .replace("！", " ")
                .replace(",", " ")
                .replace(".", " ")
                .replace("?", " ")
                .replace("!", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

        // 3. 若标准化后为空，返回空列表
        if (normalized.isBlank()) {
            return List.of();
        }

        // 4. 按空白切分，过滤空串和长度小于 2 的词，去重后返回
        return Arrays.stream(normalized.split("\\s+"))
                .filter(term -> term != null && !term.isBlank())
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }
    
    /**
     * 标准化问题文本。
     *
     * <p>去除首尾空白并转为小写，以便后续进行大小写不敏感的匹配。
     *
     * @param text 用户问题
     * @return 标准化问题文本
     */
    private String normalize(String text) {
        // 1. 空值处理为 empty string，否则去空白并转小写
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 查询指定文档集合下的可用分块。
     *
     * <p>按文档 ID 集合过滤，仅返回 enabled=1 的分块，
     * 并按 documentId、chunkIndex 升序排列。
     *
     * @param documentIds 文档 ID 集合
     * @return 分块列表
     */
    private List<KbChunk> loadAvailableChunk(Set<Long> documentIds) {
        // 1. 校验文档 ID 集合是否为空
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

        // 2. 查询数据库中符合条件的分块列表
        return kbChunkRepository.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .in(KbChunk::getDocumentId, documentIds)
                        .eq(KbChunk::getEnabled, 1)
                        .orderByAsc(KbChunk::getDocumentId)
                        .orderByAsc(KbChunk::getChunkIndex)
        );
    }

    /**
     * 查询当前用户在指定知识库下可用的文档。
     *
     * <p>先通过 ACL 服务获取可读文档 ID 集合，再过滤出
     * 未删除、已启用、解析状态为 SUCCESS 的文档，按创建时间降序排列。
     *
     * @param kbId    知识库 ID
     * @param userId  当前用户 ID
     * @return 文档列表
     */
    private List<KbDocument> loadAvailableDocuments(Long kbId, Long userId) {

        // 1. 获取当前用户在知识库下的可读文档 ID 集合
        Set<Long> readableDocumentIds = documentAclService.listReadableDocumentIds(userId, kbId);
        if (readableDocumentIds == null || readableDocumentIds.isEmpty()) {
            log.info("[KB][ACL] 当前用户在知识库下无可读文档 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        // 2. 查询数据库中符合 ACL 和状态条件的文档列表
        return kbDocumentRepository.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .in(KbDocument::getId, readableDocumentIds)
                        .eq(KbDocument::getDeleted, 0)
                        .eq(KbDocument::getEnabled, 1)
                        .eq(KbDocument::getParseStatus, "SUCCESS")
//                        .eq(KbDocument::getStatus, DOCUMENT_STATUS_SUCCESS)
                        .orderByDesc(KbDocument::getCreatedAt)
        );
    }

    /**
     * 校验检索参数。
     *
     * <p>依次检查 kbId、userId、question、topK 的合法性，
     * 任一参数不合法均抛出 {@link BusinessException}。
     *
     * @param kbId     知识库 ID
     * @param userId   当前用户 ID
     * @param question 用户问题
     * @param topK     返回结果数量
     */
    private void validateSearchParams(Long kbId, Long userId, String question, Integer topK) {
        // 1. 校验知识库 ID
        if (kbId == null || kbId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "kbId 不能为空");
        }
        // 2. 校验用户 ID
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        // 3. 校验问题内容
        if (question == null || question.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }
        // 4. 校验返回数量
        if (topK == null || topK <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "topK 不能为空");
        }
    }

    /**
     * 获取知识库分块索引名称。
     *
     * <p>优先从配置属性中读取，若未配置则返回默认名称 {@code kb_chunks_v1}。
     *
     * @return 索引名称
     */
    private String getIndexName() {
        // 1. 从配置中读取索引名称，若为空则返回默认值
        String indexName = knowledgeBaseProperties.getElasticsearch().getIndexName();
        return (indexName == null || indexName.isBlank()) ? "kb_chunks_v1" : indexName;
    }

    /**
     * 将基础类型数组转换为 Elasticsearch 查询向量列表。
     *
     * @param vector 原始向量
     * @return 向量列表
     */
    private List<Float> convertVector(float[] vector) {
        // 1. 初始化结果列表
        List<Float> result = new ArrayList<>();
        
        // 2. 若向量为空，直接返回空列表
        if (vector == null || vector.length == 0) {
            return result;
        }

        // 3. 遍历原始向量，逐个装箱并加入列表
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    /**
     * 按文档范围过滤检索结果。
     *
     * <p>仅保留结果中 documentId 存在于给定文档 ID 集合内的项，
     * 用于在向量检索和关键词检索后对结果进行二次 ACL 收口。
     *
     * @param results     原始检索结果
     * @param documentIds 允许的文档 ID 集合
     * @return 过滤后的检索结果
     */
    private List<ChunkSearchResult> filterByDocumentScope(List<ChunkSearchResult> results, Set<Long> documentIds) {
        // 1. 若结果为空或文档范围为空，直接返回空列表
        if (results == null || results.isEmpty() || documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        // 2. 使用流过滤出 documentId 在允许范围内的结果
        return results.stream()
                .filter(item -> item.getDocumentId() != null && documentIds.contains(item.getDocumentId()))
                .toList();
    }

    /**
     * 计算检索候选池大小。
     *
     * <p>检索层最终版采用“两段式”策略：
     * 先扩大候选召回范围，再做融合排序。
     *
     * @param topK 用户请求的最终结果数量
     * @return 候选池大小
     */
    private int getCandidateTopK(Integer topK) {
        if (topK == null || topK < 0) {
            return DEFAULT_CANDIDATE_TOP_K;
        }
        return Math.max(topK * 3, DEFAULT_CANDIDATE_TOP_K);
    }

    /**
     * 计算最终输出给 organizer 的 raw candidate 数量。
     *
     * <p>这里仍然不直接等于用户请求的 topK，
     * 因为 organizer 后续还会做去重、裁剪和证据整理。
     *
     * @param topK 用户请求的最终结果数量
     * @return raw candidate 数量
     */
    private int getFinalCandidateTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_FINAL_CANDIDATE_TOP_K;
        }
        return Math.max(topK * 2, DEFAULT_FINAL_CANDIDATE_TOP_K);
    }

    /**
     * 对融合后的 raw candidates 执行轻量文档平衡。
     *
     * <p>当前阶段的目标不是替代 organizer 做最终文档裁剪，
     * 而是在检索层先避免单个文档过度占满候选位。
     *
     * <p>策略：
     * <ul>
     *     <li>结果已按融合分降序排列</li>
     *     <li>遍历时优先保留高分结果</li>
     *     <li>同一文档最多保留固定数量</li>
     * </ul>
     *
     * <p>这样做的收益：
     * <ul>
     *     <li>提升候选来源多样性</li>
     *     <li>减轻 organizer 后续整理压力</li>
     *     <li>减少单文档重复证据过早占位</li>
     * </ul>
     *
     * @param mergedResults 融合排序后的 raw candidates
     * @return 轻量文档平衡后的 raw candidates
     */
    private List<ChunkSearchResult> balanceResultsByDocument(List<ChunkSearchResult> mergedResults) {
        log.info("[KB][SEARCH][BALANCE] 开始执行 raw candidate 文档平衡 - inputCount={}, maxPerDocument={}",
                mergedResults == null ? 0 : mergedResults.size(),
                MAX_RAW_CANDIDATES_PER_DOCUMENT);
        
        // 1. 空结果直接返回，避免无意义遍历。
        if (mergedResults == null || mergedResults.isEmpty()) {
            return List.of();
        }

        List<ChunkSearchResult> balancedResults = new ArrayList<>();
        // 初始化文档计数器，用于记录每个文档已保留数量。
        Map<Long, Integer> documentCounter = new HashMap<>();
        int droppedCount = 0;

        for (ChunkSearchResult result : mergedResults) {
            // 2. documentId 为空时无法参与文档平衡，当前阶段直接跳过。
            if (result == null || result.getDocumentId() == null) {
                droppedCount++;
                continue;
            }
            
            // 3. 计算当前文档已保留数量
            int currentCount = documentCounter.getOrDefault(result.getDocumentId(), 0);
            
            // 4. 若当前文档已达到 raw candidate 阶段上限，则丢弃后续低分结果。
            if (currentCount >= MAX_RAW_CANDIDATES_PER_DOCUMENT) {
                droppedCount++;
                log.debug("[KB][SEARCH][BALANCE] 单文档 raw candidate 数达到上限，丢弃结果 - documentId={}, chunkId={}, score={}",
                        result.getDocumentId(), result.getChunkId(), result.getScore());
                continue;
            }

            // 5. 保留当前结果并更新计数。
            balancedResults.add(result);
            documentCounter.put(result.getDocumentId(), currentCount + 1);
        }
        
        log.info("[KB][SEARCH][BALANCE] raw candidate 文档平衡完成 - inputCount={}, outputCount={}, droppedCount={}, documentCount={}",
                mergedResults.size(), balancedResults.size(), droppedCount, documentCounter.size());

        return balancedResults;
    }

    /**
     * 检索融合权重。
     *
     * @param vectorWeight 向量结果权重
     * @param bm25Weight   BM25 结果权重
     */
    private record RetrievalWeights(double vectorWeight, double bm25Weight) {
    }

    /**
     * 判断 query 是否更像工程术语/标识符类查询。
     *
     * <p>当前采用轻量规则，不依赖模型或复杂分类器。
     * 该规则主要用于区分：
     * <ul>
     *     <li>接口名、配置项、路径、权限名等术语型 query</li>
     *     <li>普通自然语言问题</li>
     * </ul>
     *
     * @param question 用户问题
     * @return true 表示更像术语型 query
     */
    private boolean isTermLikeQuery(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }

        String trimmed = question.trim();

        // 1. 路径、下划线、连接符等符号型 query，优先视为术语型。
        if (trimmed.contains("/") || trimmed.contains("_") || trimmed.contains("-")) {
            return true;
        }

        // 2. 存在 camelCase 结构时，通常也是工程标识符。
        if (trimmed.matches(".*[a-z][A-Z].*")) {
            return true;
        }

        // 3. 全大写短词通常是权限名、枚举值或缩写。
        if (trimmed.matches("^[A-Z0-9_]{2,20}$")) {
            return true;
        }

        // 4. 没有空格、长度较短的单 token，也更像工程术语。
        if (!trimmed.contains(" ") && trimmed.length() <= 24) {
            return true;
        }

        return false;
    }

    /**
     * 根据 query 类型决定融合权重。
     *
     * <p>当前策略：
     * <ul>
     *     <li>术语型 query：BM25 更强</li>
     *     <li>自然语言 query：向量更强</li>
     * </ul>
     *
     * @param question 用户问题
     * @return 检索融合权重
     */
    private RetrievalWeights resolveRetrievalWeights(String question) {
        boolean termLike = isTermLikeQuery(question);

        if (termLike) {
            log.info("[KB][SEARCH][MERGE] 当前 query 判定为术语型，使用术语型融合权重 - question={}", question);
            return new RetrievalWeights(TERM_LIKE_VECTOR_WEIGHT, TERM_LIKE_BM25_WEIGHT);
        }

        log.info("[KB][SEARCH][MERGE] 当前 query 判定为自然语言型，使用自然语言融合权重 - question={}", question);
        return new RetrievalWeights(NATURAL_LANGUAGE_VECTOR_WEIGHT, NATURAL_LANGUAGE_BM25_WEIGHT);
    }

}
