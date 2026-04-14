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
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.EmbeddingService;
import com.jujiu.agent.service.kb.VectorSearchService;
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
     
        // 5. 执行向量检索，并按文档范围过滤结果
        List<ChunkSearchResult> vectorResults = vectorSearch(kbId, userId, question, queryVector, topK, documentIds);
        vectorResults = filterByDocumentScope(vectorResults, documentIds);
        
        // 6. 执行关键词检索，并按文档范围过滤结果
        List<ChunkSearchResult> keywordResults = keywordSearch(kbId, userId, question, topK, documentIds, documents);
        keywordResults = filterByDocumentScope(keywordResults, documentIds);
        
        // 7. 融合向量与关键词检索结果，取 topK 并重新赋排名
        List<ChunkSearchResult> mergedResults = mergeResults(vectorResults, keywordResults, topK);

        // 8. 记录检索耗时并返回结果
        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[KB][SEARCH] 检索完成 - kbId={}, userId={}, vectorResultCount={}, keywordResultCount={}, mergedCount={}, latencyMs={}",
                kbId, userId, vectorResults.size(), keywordResults.size(), mergedResults.size(), latencyMs);
        
        return mergedResults;
    }

    /**
     * 合并向量检索和关键词检索结果。
     *
     * <p>采用加权融合策略：向量结果权重 0.7，关键词结果权重 0.5，
     * 相同分块得分累加，最终按得分降序截断至 topK，并重新赋予 1-based 排名。
     *
     * @param vectorResults   向量检索结果
     * @param keywordResults  关键词检索结果
     * @param topK            返回数量
     * @return 合并后的检索结果
     */
    private List<ChunkSearchResult> mergeResults(List<ChunkSearchResult> vectorResults, 
                                                 List<ChunkSearchResult> keywordResults,
                                                 Integer topK) {

        log.info("[KB][SEARCH][MERGE] 开始融合检索结果 - vectorCount={}, keywordCount={}, topK={}",
                vectorResults == null ? 0 : vectorResults.size(),
                keywordResults == null ? 0 : keywordResults.size(),
                topK);

        // 1. 初始化融合结果映射表
        Map<Long, ChunkSearchResult> mergedMap = new LinkedHashMap<>();
        
        // 2. 将向量检索结果按权重 0.7 合并到映射表
        mergeInToMap(mergedMap, vectorResults, 0.7D);
        
        // 3. 将关键词检索结果按权重 0.5 合并到映射表
        mergeInToMap(mergedMap, keywordResults, 0.5D);

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

        log.info("[KB][SEARCH][KEYWORD] 关键词检索完成 - kbId={}, userId={}, chunkCandidateCount={}, resultCount={}",
                kbId, userId, chunks.size(), sortedResults.size());
        
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
     * 基于问题和分块数据构造检索结果。
     *
     * <p>当前采用简单的文本匹配打分策略：
     * <ul>
     *     <li>完整问题命中加更高分</li>
     *     <li>问题分词后按关键词命中累计加分</li>
     *     <li>标题命中给予额外加分</li>
     * </ul>
     *
     * @param question   用户问题
     * @param documents  文档列表
     * @param chunks     分块列表
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
     * @param normalizedQuestion  标准化后的问题
     * @param questionTerms       问题拆词结果
     * @param document            文档对象
     * @param chunk               分块对象
     * @return 分数
     */
    private Double calculateScore(String normalizedQuestion, 
                                  List<String> questionTerms, 
                                  KbDocument document, 
                                  KbChunk chunk) {
        // 1. 对文档标题和分块内容做标准化
        String normalizedTitle = normalize(document.getTitle());
        String normalizedContent = normalize(chunk.getContent());
        
        double score = 0D;

        // 2. 完整问题命中加分（正文 +10，标题 +6）
        if (!normalizedQuestion.isBlank()) {
            if (normalizedContent.contains(normalizedQuestion)) {
                score += 10D;
            }
            if (normalizedTitle.contains(normalizedQuestion)) {
                score += 6D;
            }
        }

        // 3. 关键词命中累计加分（正文每个 +2，标题每个 +1）
        for (String term : questionTerms) {
            if (normalizedContent.contains(term)) {
                score += 2D;
            }
            if (normalizedTitle.contains(term)) {
                score += 1D;
            }
        }

        // 4. 内容过短做轻微降权，并保证最终得分不小于 0
        if (chunk.getCharCount() != null && chunk.getCharCount() < 20) {
            score -= 0.5D;
        }
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
     * 对用户问题做轻量拆词。
     *
     * <p>当前策略不追求复杂分词，只做最小可用切分：
     * 按空白和常见中文标点切分，并过滤过短词项。
     *
     * @param question 用户问题
     * @return 词项列表
     */
    private List<String> splitQuestionTerms(String question) {
        // 1. 若问题为空，直接返回空列表
        if (question == null || question.isEmpty()) {
            return List.of();
        }

        // 2. 将问题转小写，并把常见标点替换为空格
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

        // 3. 若标准化后为空，返回空列表
        if (normalized.isEmpty()) {
            return List.of();
        }

        // 4. 按空白切分，过滤空串和长度小于 2 的词，去重后返回
        return java.util.Arrays.stream(normalized.split("\\s+"))
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
}
