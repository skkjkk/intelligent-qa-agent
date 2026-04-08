package com.jujiu.agent.service.kb.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.model.entity.KbRetrievalTrace;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.repository.KbRetrievalTraceRepository;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.service.kb.VectorSearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答服务实现。
 *
 * <p>当前类是最小 RAG 闭环的核心编排入口，
 * 后续将统一承接知识库问答 API、Tool 调用和聊天增强能力。
 *
 * <p>当前阶段职责包括：
 * <ul>
 *     <li>接收请求并兜底关键参数</li>
 *     <li>调用检索服务获取分块结果</li>
 *     <li>构造知识库上下文</li>
 *     <li>调用 DeepSeekClient 生成答案</li>
 *     <li>封装引用结果并返回</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {

    private static final String DEFAULT_RETRIEVAL_MODE = "VECTOR";
    private static final String DEFAULT_QUERY_SOURCE = "KB_API";
    private static final String DEFAULT_QUERY_STATUS_SUCCESS = "SUCCESS";
    private static final String DEFAULT_QUERY_STATUS_EMPTY = "EMPTY";
    private static final String DEFAULT_QUERY_STATUS_FAILED = "FAILED";
    

    private final VectorSearchService vectorSearchService;
    private final DeepSeekClient deepSeekClient;
    private final KbQueryLogRepository kbQueryLogRepository;
    private final KbRetrievalTraceRepository kbRetrievalTraceRepository;
    public final ObjectMapper objectMapper;
    
    public RagServiceImpl(VectorSearchService vectorSearchService,
                          DeepSeekClient deepSeekClient,
                          KbQueryLogRepository kbQueryLogRepository,
                          KbRetrievalTraceRepository kbRetrievalTraceRepository,
                          ObjectMapper objectMapper) {
        this.vectorSearchService = vectorSearchService;
        this.deepSeekClient = deepSeekClient;
        this.kbQueryLogRepository = kbQueryLogRepository;
        this.kbRetrievalTraceRepository = kbRetrievalTraceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行知识库问答。
     *
     * @param userId 当前用户 ID
     * @param request 知识库问答请求
     * @return 知识库问答响应结果
     */
    @Override
    @Transactional
    public KnowledgeQueryResponse query(Long userId, QueryKnowledgeBaseRequest request) {
        validateRequest(userId, request);
        
        long startTime = System.currentTimeMillis();
        Long kbId = request.getKbId() == null ? 1L : request.getKbId();
        Integer topK = request.getTopK() == null ? 5 : request.getTopK();

        log.info("[KB][QUERY] 开始执行知识库问答 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, request.getQuestion().length()
        );

        // 检索
        List<ChunkSearchResult> searchResults = vectorSearchService.search(
                kbId, 
                userId, 
                request.getQuestion(), 
                topK);

        if (searchResults == null || searchResults.isEmpty()) {
            return handleEmptyResult(userId, kbId, request, topK, startTime);
        }

        // 构造引用列表
        List<CitationResponse> citations = buildCitation(searchResults);
        // 构造知识库上下文
        String context = buildContext(searchResults);
        // 构造提示
        List<DeepSeekMessage> messages = buildPromptMessages(request.getQuestion(), context);
        // 调用 DeepSeek
        DeepSeekResult deepSeekResult = deepSeekClient.chat(messages);

        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[KB][QUERY] 知识库问答完成 - kbId={}, userId={}, topK={}, questionLength={}, latencyMs={}",
                kbId, userId, topK, request.getQuestion().length(), latencyMs
        );

        // 保存查询日志
        KbQueryLog queryLog = saveQueryLog(
                userId,
                kbId,
                request,
                topK,
                deepSeekResult,
                citations,
                latencyMs,
                DEFAULT_QUERY_STATUS_SUCCESS,
                null
        );
        
        // 保存检索轨迹
        saveRetrievalTrace(queryLog.getId(), searchResults);

        // 封装响应结果
        return KnowledgeQueryResponse.builder()
                .answer(deepSeekResult.getReply())
                .citations(citations)
                .promptTokens(deepSeekResult.getPromptTokens())
                .completionTokens(deepSeekResult.getCompletionTokens())
                .totalTokens(deepSeekResult.getTotalTokens())
                .latencyMs(latencyMs)
                .build();
    }
    /**
     * 保存检索轨迹。
     *
     * @param queryLogId 查询日志 ID
     * @param searchResults 检索结果
     */
    private void saveRetrievalTrace(Long queryLogId, List<ChunkSearchResult> searchResults) {
        for (ChunkSearchResult searchResult : searchResults) {
            KbRetrievalTrace result = KbRetrievalTrace.builder()
                    .queryLogId(queryLogId)
                    .chunkId(searchResult.getChunkId())
                    .score(searchResult.getScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(searchResult.getScore()))
                    .rankNo(searchResult.getRank())
                    .retrievalType(DEFAULT_RETRIEVAL_MODE)
                    .createdAt(LocalDateTime.now())
                    .build();
            kbRetrievalTraceRepository.insert(result);
        }
    }

    /**
     * 保存知识库查询日志。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param request 原始请求
     * @param topK 检索数量
     * @param deepSeekResult 模型调用结果
     * @param citations 引用列表
     * @param latencyMs 总耗时
     * @param status 查询状态
     * @param errorMessage 错误信息
     * @return 查询日志实体
     */
    private KbQueryLog saveQueryLog(Long userId,
                                    Long kbId,
                                    QueryKnowledgeBaseRequest request,
                                    Integer topK,
                                    DeepSeekResult deepSeekResult,
                                    List<CitationResponse> citations,
                                    long latencyMs,
                                    String status,
                                    String errorMessage) {

        KbQueryLog queryLog = KbQueryLog.builder()
                .kbId(kbId)
                .userId(userId)
                .sessionId(null)
                .querySource(DEFAULT_QUERY_SOURCE)
                .question(request.getQuestion())
                .rewrittenQuestion(null)
                .answer(deepSeekResult != null ? deepSeekResult.getReply() : "知识库中没有足够信息支持回答该问题。")
                .retrievalTopK(topK)
                .retrievalMode(DEFAULT_RETRIEVAL_MODE)
                .citedChunkIds(toCitedChunkIdsJson(citations))
                .promptTokens(deepSeekResult != null ? deepSeekResult.getPromptTokens() : 0)
                .completionTokens(deepSeekResult != null ? deepSeekResult.getCompletionTokens() : 0)
                .totalTokens(deepSeekResult != null ? deepSeekResult.getTotalTokens() : 0)
                .latencyMs(Math.toIntExact(latencyMs))
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();
        kbQueryLogRepository.insert(queryLog);
        
        return queryLog;
    }

    /**
     * 将引用中的 chunkId 列表转换为 JSON 字符串。
     *
     * @param citations 引用列表
     * @return JSON 字符串
     */
    private String toCitedChunkIdsJson(List<CitationResponse> citations) {
        List<Long> chunkIds = citations.stream()
                .map(CitationResponse::getChunkId)
                .toList();
        try {
            return objectMapper.writeValueAsString(chunkIds);
        } catch (JsonProcessingException e) {
            log.error("[KB][QUERY] 引用 chunkId 转换为 JSON 序列化失败 - chunkIds={}", chunkIds, e);
            return "[]";
        }
    }
    
    private List<DeepSeekMessage> buildPromptMessages(@NotBlank(message = "问题不能为空") 
                                                      @Size(max = 2000, message = "问题长度不能超过2000字符") 
                                                      String question, 
                                                      String context) {
        String prompt = """
                你是一个知识库问答助手。请严格基于参考资料回答问题。
                如果参考资料不足，请明确说“知识库中没有足够信息支持回答该问题”。
                
                【参考资料】
                %s
                
                【问题】
                %s
                
                【回答要求】
                1. 仅基于参考资料回答
                2. 使用中文
                3. 不要编造内容
                4. 如果引用资料，可标注[1][2]
                """.formatted(context, question);
        DeepSeekMessage userMessage = new DeepSeekMessage();
        userMessage.setRole(DeepSeekMessage.MessageRole.USER);
        userMessage.setContent(prompt);
        return List.of(userMessage);
    }
    
    /**
     * 构造知识库上下文。
     *
     * <p>当前采用最简单的编号拼接方式，便于模型引用和前端溯源。
     *
     * @param searchResults 检索结果
     * @return 上下文文本
     */
    private String buildContext(List<ChunkSearchResult> searchResults) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            ChunkSearchResult searchResult = searchResults.get(i);
            builder.append("[").append(i + 1).append("] ")
                    .append(searchResult.getDocumentTitle())
                    .append("\n")
                    .append(searchResult.getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    /**
     * 处理零命中场景。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param request 原始请求
     * @param topK 检索数量
     * @param startTime 开始时间
     * @return 问答响应
     */
    private KnowledgeQueryResponse handleEmptyResult(Long userId, 
                                                     Long kbId, 
                                                     QueryKnowledgeBaseRequest request, 
                                                     Integer topK, 
                                                     Long startTime) {
        long latencyMs = System.currentTimeMillis() - startTime;
        String answer = "抱歉，知识库中没有足够信息支持回答该问题。";

        saveQueryLog(
                userId,
                kbId,
                request,
                topK,
                null,
                List.of(),
                latencyMs,
                DEFAULT_QUERY_STATUS_EMPTY,
                null
        );

        return KnowledgeQueryResponse.builder()
                .answer(answer)
                .citations(List.of())
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .latencyMs(latencyMs)
                .build();
    }

    /**
     * 构造引用列表。
     *
     * @param searchResults 检索结果
     * @return 引用响应列表
     */
    private List<CitationResponse> buildCitation(List<ChunkSearchResult> searchResults) {
        List<CitationResponse> citations = new ArrayList<>();
        for (ChunkSearchResult searchResult : searchResults) {
            citations.add(
                    CitationResponse.builder()
                            .chunkId(searchResult.getChunkId())
                            .documentId(searchResult.getDocumentId())
                            .documentTitle(searchResult.getDocumentTitle())
                            .snippet(searchResult.getContent())
                            .score(searchResult.getScore())
                            .rank(searchResult.getRank())
                            .build()
            );
        }
        return citations;
    }
    private void validateRequest(Long userId, QueryKnowledgeBaseRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "request 不能为空");
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }
    }
}
