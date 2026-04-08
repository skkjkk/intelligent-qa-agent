package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbChunkRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
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
    
    public VectorSearchServiceImpl(EmbeddingService embeddingService,
                                   KbDocumentRepository kbDocumentRepository,
                                   KbChunkRepository kbChunkRepository) {
        this.embeddingService = embeddingService;
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbChunkRepository = kbChunkRepository;
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

        log.info("[KB][SEARCH] 开始执行检索 - kbId={}, userId={}, topK={}", kbId, userId, topK);

        // 当前过渡版本不真正使用 embedding 结果参与排序，
        // 但保留该调用用于提前验证向量化链路是否可用。
        // TODO: embedding向量化检索

        try {
            embeddingService.embedQuery(question);
        } catch (Exception e) {
            log.debug("[KB][SEARCH] Embedding 尚未实现，继续走临时文本检索 - kbId={}, userId={}", kbId, userId, e);
        }
        
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

        // 加载可用分块
        List<KbChunk> chunks = loadAvailableChunk(documentIds);
        if (chunks.isEmpty()) {
            log.info("[KB][SEARCH] 当前知识库下无可用分块 - kbId={}, userId={}", kbId, userId);
            return List.of();
        }

        // 构造检索结果
        List<ChunkSearchResult> matchedResults  = buildSearchResult(question, documents, chunks);
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

        return sortedResults;
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
}
