package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.request.QueryFeedbackRequest;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.model.dto.response.KbQueryHistoryResponse;
import com.jujiu.agent.model.entity.KbQueryFeedback;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.model.entity.KbRetrievalTrace;
import com.jujiu.agent.repository.KbQueryFeedbackRepository;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.repository.KbRetrievalTraceRepository;
import com.jujiu.agent.service.kb.QueryLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库查询日志服务实现。
 *
 * <p>统一负责知识库问答日志与检索轨迹的落库与历史查询，
 * 为后续反馈、统计与效果评估提供基础数据支撑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Service
@Slf4j
public class QueryLogServiceImpl implements QueryLogService {
    private static final String DEFAULT_QUERY_SOURCE = "KB_API";
    private static final String DEFAULT_RETRIEVAL_MODE = "HYBRID";
    
    private final KbQueryLogRepository kbQueryLogRepository;
    private final KbRetrievalTraceRepository kbRetrievalTraceRepository;
    private final ObjectMapper objectMapper;
    private final KbQueryFeedbackRepository kbQueryFeedbackRepository;
    public QueryLogServiceImpl(KbQueryLogRepository kbQueryLogRepository, 
                               KbRetrievalTraceRepository kbRetrievalTraceRepository, 
                               ObjectMapper objectMapper,
                               KbQueryFeedbackRepository kbQueryFeedbackRepository) {
        this.kbQueryLogRepository = kbQueryLogRepository;
        this.kbRetrievalTraceRepository = kbRetrievalTraceRepository;
        this.objectMapper = objectMapper;
        this.kbQueryFeedbackRepository = kbQueryFeedbackRepository;
    }

    /**
     * 保存知识库问答日志。
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
    @Override
    public KbQueryLog saveQueryLog(Long userId, 
                                   Long kbId, 
                                   QueryKnowledgeBaseRequest request, 
                                   Integer topK, 
                                   DeepSeekResult deepSeekResult, 
                                   List<CitationResponse> citations, 
                                   long latencyMs, 
                                   String status, 
                                   String errorMessage) {
        KbQueryLog queryLog = KbQueryLog.builder()
                .userId(userId)
                .kbId(kbId)
                .sessionId(null)
                .querySource(DEFAULT_QUERY_SOURCE)
                .question(request.getQuestion())
                .rewrittenQuestion(null)
                .answer(deepSeekResult != null ? deepSeekResult.getReply() : "知识库中没有足够信息支持回答该问题。")
                .retrievalTopK(topK)
                .retrievalMode(DEFAULT_RETRIEVAL_MODE)
                .citedChunkIds(toCitedChunkIdsJson(citations))
                .promptTokens(deepSeekResult != null ? deepSeekResult.getPromptTokens() : null)
                .completionTokens(deepSeekResult != null ? deepSeekResult.getCompletionTokens() : null)
                .totalTokens(deepSeekResult != null ? deepSeekResult.getTotalTokens() : null)
                .latencyMs(Math.toIntExact(latencyMs))
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();

        kbQueryLogRepository.insert(queryLog);
        
        log.info("[KB][QUERY_LOG] 查询日志保存成功 - queryLogId={}, kbId={}, userId={}, status={}",
                queryLog.getId(), kbId, userId, status);

        return queryLog;
    }

    /**
     * 保存检索轨迹。
     *
     * @param queryLogId 查询日志 ID
     * @param searchResults 检索结果
     */
    @Override
    public void saveRetrievalTrace(Long queryLogId, List<ChunkSearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            log.info("[KB][QUERY_LOG] 无检索轨迹需要保存 - queryLogId={}", queryLogId);
            return;
        }
        for (ChunkSearchResult searchResult : searchResults) {
            KbRetrievalTrace trace  = KbRetrievalTrace.builder()
                    .queryLogId(queryLogId)
                    .chunkId(searchResult.getChunkId())
                    .score(searchResult.getScore() == null ?BigDecimal.ZERO : BigDecimal.valueOf(searchResult.getScore()))
                    .rankNo(searchResult.getRank())
                    .retrievalType(DEFAULT_RETRIEVAL_MODE)
                    .createdAt(LocalDateTime.now())
                    .build();
            kbRetrievalTraceRepository.insert(trace);
        }

        log.info("[KB][QUERY_LOG] 检索轨迹保存成功 - queryLogId={}, traceCount={}",
                queryLogId, searchResults.size());
    }
    
    /**
     * 查询用户知识库问答历史。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 历史问答列表
     */
    @Override
    public List<KbQueryHistoryResponse> listQueryHistory(Long userId, Long kbId) {
        LambdaQueryWrapper<KbQueryLog> wrapper = new LambdaQueryWrapper<KbQueryLog>()
                .eq(KbQueryLog::getUserId, userId)
                .orderByDesc(KbQueryLog::getCreatedAt);

        if (kbId != null) {
            wrapper.eq(KbQueryLog::getKbId, kbId);
        }

        List<KbQueryLog> queryLogs = kbQueryLogRepository.selectList(wrapper);
        
        log.info("[KB][QUERY_LOG] 查询问答历史成功 - userId={}, kbId={}, count={}",
                userId, kbId, queryLogs.size());

        return queryLogs.stream()
                .map(logItem -> KbQueryHistoryResponse.builder()
                        .queryLogId(logItem.getId())
                        .kbId(logItem.getKbId())
                        .question(logItem.getQuestion())
                        .answer(logItem.getAnswer())
                        .querySource(logItem.getQuerySource())
                        .retrievalMode(logItem.getRetrievalMode())
                        .retrievalTopK(logItem.getRetrievalTopK())
                        .status(logItem.getStatus())
                        .latencyMs(logItem.getLatencyMs())
                        .totalTokens(logItem.getTotalTokens())
                        .createdAt(logItem.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public void saveFeedback(Long userId, Long queryLogId, QueryFeedbackRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (queryLogId == null || queryLogId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "queryLogId 不能为空");
        }
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "反馈请求不能为空");
        }

        KbQueryLog queryLog = kbQueryLogRepository.selectById(queryLogId);
        if (queryLog == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "查询记录不存在");
        }

        if (!userId.equals(queryLog.getUserId())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "无权反馈该查询记录");
        }

        KbQueryFeedback feedback = KbQueryFeedback.builder()
                .queryLogId(queryLogId)
                .userId(userId)
                .helpful(Boolean.TRUE.equals(request.getHelpful()) ? 1 : 0)
                .rating(request.getRating())
                .feedbackContent(request.getFeedbackContent())
                .createdAt(LocalDateTime.now())
                .build();

        kbQueryFeedbackRepository.insert(feedback);

        log.info("[KB][QUERY_LOG] 查询反馈保存成功 - queryLogId={}, userId={}, helpful={}, rating={}",
                queryLogId, userId, request.getHelpful(), request.getRating());
    }

    /**
     * 将引用中的 chunkId 列表转换为 JSON 字符串。
     *
     * @param citations 引用列表
     * @return JSON 字符串
     */
    private String toCitedChunkIdsJson(List<CitationResponse> citations) {
        List<Long> chunkIds = citations == null
                ? List.of()
                : citations.stream().map(CitationResponse::getChunkId).toList();

        try {
            return objectMapper.writeValueAsString(chunkIds);
        } catch (JsonProcessingException e) {
            log.error("[KB][QUERY_LOG] 引用 chunkId 序列化失败 - chunkIds={}", chunkIds, e);
            return "[]";
        }
    }
}
