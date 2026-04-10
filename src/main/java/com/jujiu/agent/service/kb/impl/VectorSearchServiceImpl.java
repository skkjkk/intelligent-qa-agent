package com.jujiu.agent.service.kb.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import com.jujiu.agent.service.kb.EmbeddingService;
import com.jujiu.agent.service.kb.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索服务实现。
 *
 * <p>负责执行用户问题的检索流程，包括：
 * <ul>
 *     <li>问题向量化</li>
 *     <li>调用 Elasticsearch 执行检索</li>
 *     <li>返回标准化的分块检索结果</li>
 * </ul>
 *
 * <p>当前阶段该类先保留最小骨架，
 * 后续优先实现向量检索，再逐步扩展混合检索能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Service
@Slf4j
public class VectorSearchServiceImpl implements VectorSearchService {
    
    private final EmbeddingService embeddingService;
    private final KbDocumentRepository kbDocumentRepository;
    private final KbChunkRepository kbChunkRepository;
    private final ElasticsearchClient elasticsearchClient;
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    public VectorSearchServiceImpl(EmbeddingService embeddingService,
                                   KbDocumentRepository kbDocumentRepository,
                                   KbChunkRepository kbChunkRepository,
                                   ElasticsearchClient elasticsearchClient,
                                   KnowledgeBaseProperties knowledgeBaseProperties) {
        this.embeddingService = embeddingService;
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbChunkRepository = kbChunkRepository;
        this.elasticsearchClient = elasticsearchClient;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
    }


    /**
     * 执行知识库检索。
     *
     * <p>当前版本先使用数据库中的文档和分块数据执行简单文本匹配检索，
     * 以便快速打通最小 RAG 闭环。
     *
     * @param kbId 知识库 ID
     * @param userId 当前用户 ID
     * @param question 用户问题
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    @Override
    public List<ChunkSearchResult> search(Long kbId, Long userId, String question, Integer topK) {
        validateSearchParams(kbId, userId, question, topK);
        
        long startTime = System.currentTimeMillis();
        log.info("[KB][SEARCH] 开始执行检索 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, question.length());

        // 加载可用文档
        List<KbDocument> documents = loadAvailableDocuments(kbId, userId);
        if (documents.isEmpty()) {
            log.info("[KB][SEARCH] 当前知识库下无可用文档 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        // 获取文档ids
        Set<Long> documentIds = documents.stream()
                .map(KbDocument::getId)
                .collect(Collectors.toSet());

        float[] queryVector = embeddingService.embedQuery(question);
     
        List<ChunkSearchResult> vectorResults = vectorSearch(kbId,userId, question, queryVector, topK, documentIds);
        List<ChunkSearchResult> keywordResults = keywordSearch(kbId, userId, question, topK, documentIds, documents);
        List<ChunkSearchResult> mergedResults = mergeResults(vectorResults, keywordResults, topK);

        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[KB][SEARCH] 检索完成 - kbId={}, userId={}, vectorResultCount={}, keywordResultCount={}, mergedCount={}, latencyMs={}",
                kbId, userId, vectorResults.size(), keywordResults.size(), mergedResults.size(), latencyMs);
        
        return  mergedResults;

    }

    /**
     * 合并向量检索和关键词检索结果。
     *
     * @param vectorResults 向量检索结果
     * @param keywordResults 关键词检索结果
     * @param topK 返回数量
     * @return 合并后的检索结果
     */
    private List<ChunkSearchResult> mergeResults(List<ChunkSearchResult> vectorResults, 
                                                 List<ChunkSearchResult> keywordResults,
                                                 Integer topK) {

        log.info("[KB][SEARCH][MERGE] 开始融合检索结果 - vectorCount={}, keywordCount={}, topK={}",
                vectorResults == null ? 0 : vectorResults.size(),
                keywordResults == null ? 0 : keywordResults.size(),
                topK);

        Map<Long, ChunkSearchResult> mergedMap = new LinkedHashMap<>();
        
        mergeInToMap(mergedMap, vectorResults, 0.7D);
        mergeInToMap(mergedMap, keywordResults, 0.5D);

        List<ChunkSearchResult> mergedResults = mergedMap.values().stream()
                .sorted(Comparator.comparing(
                        ChunkSearchResult::getScore,
                        Comparator.nullsLast(Double::compareTo)
                ).reversed())
                .limit(topK)
                .toList();

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
     * @param target 目标映射
     * @param source 来源结果
     * @param weight 当前来源权重
     */
    private void mergeInToMap(Map<Long, ChunkSearchResult> target, 
                              List<ChunkSearchResult> source, 
                              double weight) {
        if (source == null || source.isEmpty()) {
            return;
        }

        for (ChunkSearchResult result : source) {
            if (result == null || result.getChunkId() == null) {
                continue;
            }

            double weightedScore = (result.getScore() == null ? 0D : result.getScore()) * weight;
            ChunkSearchResult existing = target.get(result.getChunkId());
            

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
     * <p>当前版本先复用数据库分块与轻量文本匹配逻辑，
     * 后续可替换为 Elasticsearch BM25 检索实现。
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
        if (documentIds.isEmpty() || documentIds == null) {
            log.info("[KB][SEARCH][KEYWORD] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        log.info("[KB][SEARCH][KEYWORD] 开始关键词检索 - kbId={}, userId={}, topK={}, candidateDocumentCount={}",
                kbId, userId, topK, documentIds.size());

        List<KbChunk> chunks = loadAvailableChunk(documentIds);
        if (chunks.isEmpty()) {
            log.info("[KB][SEARCH][KEYWORD] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        List<ChunkSearchResult> matchedResults = buildSearchResult(question, documents, chunks);

        List<ChunkSearchResult> sortedResults = matchedResults.stream()
                .sorted(Comparator.comparing(
                                ChunkSearchResult::getScore,
                                Comparator.nullsLast(Double::compareTo)
                ).reversed())
                .limit(topK)
                .toList();
        
        for (int i = 0; i < sortedResults.size(); i++) {
            sortedResults.get(i).setRank(i + 1);
        }

        log.info("[KB][SEARCH][KEYWORD] 关键词检索完成 - kbId={}, userId={}, chunkCandidateCount={}, resultCount={}",
                kbId, userId, chunks.size(), sortedResults.size());
        
        return sortedResults;
    }

    /**
     * 执行向量检索。
     *
     * @param kbId 知识库 ID
     * @param userId 当前用户 ID
     * @param question 用户问题
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param documentIds 可检索文档 ID 集合
     * @return 检索结果
     */
    private List<ChunkSearchResult> vectorSearch(Long kbId, 
                                                 Long userId, 
                                                 String question, 
                                                 float[] queryVector, 
                                                 Integer topK, 
                                                 Set<Long> documentIds) {
        if (queryVector == null || queryVector.length == 0) {
            log.warn("[KB][SEARCH][VECTOR] 查询向量为空 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }

        if (documentIds == null || documentIds.isEmpty()) {
            log.info("[KB][SEARCH][VECTOR] 无可检索文档范围 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }
        
        String indexName = getIndexName();
        log.info("[KB][SEARCH][VECTOR] 开始向量检索 - kbId={}, userId={}, topK={}, indexName={}, candidateDocumentCount={}, vectorDimension={}",
                kbId, userId, topK, indexName, documentIds.size(), queryVector.length);
        try {
            List<Float> vector = convertVector(queryVector);
            
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

            List<ChunkSearchResult> results = new ArrayList<>();
            if (response.hits() == null || response.hits().hits().isEmpty()) {
                log.info("[KB][SEARCH][VECTOR] 向量检索无命中结果 - kbId={}, userId={}", kbId, userId);
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
     * 基于问题和分块数据构造检索结果。
     *
     * <p>当前采用简单的文本匹配打分策略：
     * <ul>
     *     <li>完整问题命中加更高分</li>
     *     <li>问题分词后按关键词命中累计加分</li>
     *     <li>标题命中给予额外加分</li>
     * </ul>
     *
     * @param question 用户问题
     * @param documents 文档列表
     * @param chunks 分块列表
     * @return 检索结果列表
     */
    private List<ChunkSearchResult> buildSearchResult(String question, 
                                                      List<KbDocument> documents, 
                                                      List<KbChunk> chunks) {

        List<ChunkSearchResult> results = new ArrayList<>();
        String normalizedQuestion = normalize(question);
        List<String> questionTerms = splitQuestionTerms(question);

        for (KbChunk chunk : chunks) {
            KbDocument document = findDocument(documents, chunk.getDocumentId());
            if (document == null) {
                continue;
            }

            Double score = calculateScore(normalizedQuestion, questionTerms, document, chunk);
            if (score <= 0) {
                continue;
            }
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
     * 计算分块命中分数。
     *
     * <p>当前采用轻量规则：
     * <ul>
     *     <li>正文包含完整问题：+10</li>
     *     <li>标题包含完整问题：+6</li>
     *     <li>每命中一个有效关键词：正文 +2，标题 +1</li>
     *     <li>内容过短做轻微降权，避免噪声片段排太前</li>
     * </ul>
     *
     * @param normalizedQuestion 标准化后的问题
     * @param questionTerms 问题拆词结果
     * @param document 文档对象
     * @param chunk 分块对象
     * @return 分数
     */
    private Double calculateScore(String normalizedQuestion, 
                                 List<String> questionTerms, 
                                 KbDocument document, 
                                 KbChunk chunk) {
        String normalizedTitle = normalize(document.getTitle());
        String normalizedContent = normalize(chunk.getContent());
        
        double score = 0D;

        if (!normalizedQuestion.isBlank()) {
            if (normalizedContent.contains(normalizedQuestion)) {
                score += 10D;
            }
            if (normalizedTitle.contains(normalizedQuestion)) {
                score += 6D;
            }
        }

        for (String term : questionTerms) {
            if (normalizedContent.contains(term)) {
                score += 2D;
            }
            if (normalizedTitle.contains(term)) {
                score += 1D;
            }
        }

        if (chunk.getCharCount() != null && chunk.getCharCount() < 20) {
            score -= 0.5D;
        }
        return Math.max(score, 0D);
    }
    
    /**
     * 查找指定分块所属文档。
     *
     * @param documents 文档列表
     * @param documentId 文档 ID
     * @return 文档对象，找不到时返回 {@code null}
     */
    private KbDocument findDocument(List<KbDocument> documents, Long documentId) {
        for (KbDocument document : documents) {
            if (document.getId().equals(documentId)) {
                return document;
            }
        }
        return null;
    }

    /**
     * 对用户问题做轻量拆词。
     *
     * <p>当前策略不追求复杂分词，只做最小可用切分：
     * 按空白和常见中文标点切分，并过滤过短词项。
     *
     * @param question 用户问题
     * @return 词项列表
     */
    private List<String> splitQuestionTerms(String question) {
        if (question == null || question.isEmpty()) {
            return List.of();
        }

        String normalized = question.toLowerCase(Locale.ROOT)
                .replace("，", " ")
                .replace("。", " ")
                .replace("？", " ")
                .replace("！", " ")
                .replace(",", " ")
                .replace(".", " ")
                .replace("?", " ")
                .replace("!", " ")
                .trim();

        if (normalized.isEmpty()) {
            return List.of();
        }

        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(term -> term != null && !term.isBlank())
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }
    
    /**
     * 标准化问题文本。
     *
     * <p>当前阶段仅对问题进行简单分词，
     * 以便后续扩展支持中文分词。
     *
     * @param text 用户问题
     * @return 标准化问题文本
     */
    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 查询指定文档集合下的可用分块。
     *
     * @param documentIds 文档 ID 集合
     * @return 分块列表
     */
    private List<KbChunk> loadAvailableChunk(Set<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

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
     * <p>当前阶段先按“文档所有者 = 当前用户”收口，
     * 后续再扩展 ACL、团队共享和公共文档等能力。
     *
     * @param kbId 知识库 ID
     * @param userId 当前用户 ID
     * @return 文档列表
     */
    private List<KbDocument> loadAvailableDocuments(Long kbId, Long userId) {

        return kbDocumentRepository.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .eq(KbDocument::getOwnerUserId, userId)
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
     * @param kbId 知识库 ID
     * @param userId 当前用户 ID
     * @param question 用户问题
     * @param topK 返回结果数量
     */
    private void validateSearchParams(Long kbId, Long userId, String question, Integer topK) {
        if (kbId == null || kbId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "kbId 不能为空");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (question == null || question.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }
        if (topK == null || topK <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "topK 不能为空");
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
     * 将基础类型数组转换为 Elasticsearch 查询向量列表。
     *
     * @param vector 原始向量
     * @return 向量列表
     */
    private List<Float> convertVector(float[] vector) {
        List<Float> result = new ArrayList<>();
        if (vector == null || vector.length == 0) {
            return result;
        }

        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

}
