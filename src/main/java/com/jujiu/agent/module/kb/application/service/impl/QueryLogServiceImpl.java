package com.jujiu.agent.module.kb.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmResult;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.application.model.ChunkSearchResult;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.api.request.QueryFeedbackRequest;
import com.jujiu.agent.module.kb.api.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.module.kb.api.response.CitationResponse;
import com.jujiu.agent.module.kb.api.response.KbQueryHistoryResponse;
import com.jujiu.agent.module.kb.domain.entity.KbQueryFeedback;
import com.jujiu.agent.module.kb.domain.entity.KbQueryLog;
import com.jujiu.agent.module.kb.domain.entity.KbRetrievalTrace;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryFeedbackMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryLogMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbRetrievalTraceMapper;
import com.jujiu.agent.module.kb.application.service.QueryLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库查询日志服务实现。
 *
 * <p>统一负责知识库问答日志与检索轨迹的落库与历史查询，
 * 同时支持用户对问答结果的反馈收集。
 * 为后续反馈、统计、效果评估与链路优化提供基础数据支撑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Service
@Slf4j
public class QueryLogServiceImpl implements QueryLogService {
    /** 默认查询来源。 */
    private static final String DEFAULT_QUERY_SOURCE = "KB_API";

    /** 默认检索模式。 */
    private static final String DEFAULT_RETRIEVAL_MODE = "HYBRID";

    /** 查询日志仓储。 */
    private final KbQueryLogMapper kbQueryLogMapper;

    /** 检索轨迹仓储。 */
    private final KbRetrievalTraceMapper kbRetrievalTraceMapper;

    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /** 查询反馈仓储。 */
    private final KbQueryFeedbackMapper kbQueryFeedbackMapper;
    /**
     * 构造方法。
     *
     * @param kbQueryLogMapper 查询日志仓储
     * @param kbRetrievalTraceMapper 检索轨迹仓储
     * @param objectMapper JSON 序列化器
     * @param kbQueryFeedbackMapper 查询反馈仓储
     */
    public QueryLogServiceImpl(KbQueryLogMapper kbQueryLogMapper,
                               KbRetrievalTraceMapper kbRetrievalTraceMapper,
                               ObjectMapper objectMapper,
                               KbQueryFeedbackMapper kbQueryFeedbackMapper) {
        this.kbQueryLogMapper = kbQueryLogMapper;
        this.kbRetrievalTraceMapper = kbRetrievalTraceMapper;
        this.objectMapper = objectMapper;
        this.kbQueryFeedbackMapper = kbQueryFeedbackMapper;
    }

    /**
     * 保存知识库问答日志。
     *
     * <p>根据用户请求、模型返回结果及引用信息，构建并持久化一条查询日志记录。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param request 原始请求
     * @param topK 检索数量
     * @param llmResult 模型调用结果
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
                                   LlmResult llmResult,
                                   List<CitationResponse> citations,
                                   long latencyMs,
                                   String status,
                                   String errorMessage) {
        // 1. 构建查询日志实体，填充用户、知识库、问题、答案、引用、耗时及状态等信息
        KbQueryLog queryLog = KbQueryLog.builder()
                .userId(userId)
                .kbId(kbId)
                .sessionId(null)
                .querySource(DEFAULT_QUERY_SOURCE)
                .question(request.getQuestion())
                .rewrittenQuestion(null)
                .answer(llmResult != null ? llmResult.getReply() : "知识库中没有足够信息支持回答该问题。")
                .retrievalTopK(topK)
                .retrievalMode(DEFAULT_RETRIEVAL_MODE)
                .citedChunkIds(toCitedChunkIdsJson(citations))
                .promptTokens(llmResult != null ? llmResult.getPromptTokens() : null)
                .completionTokens(llmResult != null ? llmResult.getCompletionTokens() : null)
                .totalTokens(llmResult != null ? llmResult.getTotalTokens() : null)
                .latencyMs(Math.toIntExact(latencyMs))
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();

        // 2. 将查询日志写入数据库
        kbQueryLogMapper.insert(queryLog);

        // 3. 记录保存成功的日志并返回实体
        log.info("[KB][QUERY_LOG] 查询日志保存成功 - queryLogId={}, kbId={}, userId={}, status={}",
                queryLog.getId(), kbId, userId, status);

        return queryLog;
    }

    /**
     * 保存检索轨迹。
     *
     * <p>将单次查询命中的 Chunk 检索结果逐条记录到检索轨迹表，用于后续召回分析。
     *
     * @param queryLogId 查询日志 ID
     * @param searchResults 检索结果
     */
    @Override
    public void saveRetrievalTrace(Long queryLogId, List<ChunkSearchResult> searchResults) {
        // 1. 若检索结果为空，则直接记录日志并返回
        if (searchResults == null || searchResults.isEmpty()) {
            log.info("[KB][QUERY_LOG] 无检索轨迹需要保存 - queryLogId={}", queryLogId);
            return;
        }

        // 2. 遍历检索结果，逐条构建并保存检索轨迹实体
        for (ChunkSearchResult searchResult : searchResults) {
            KbRetrievalTrace trace = KbRetrievalTrace.builder()
                    .queryLogId(queryLogId)
                    .chunkId(searchResult.getChunkId())
                    .score(searchResult.getScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(searchResult.getScore()))
                    .rankNo(searchResult.getRank())
                    .retrievalType(DEFAULT_RETRIEVAL_MODE)
                    .createdAt(LocalDateTime.now())
                    .build();
            kbRetrievalTraceMapper.insert(trace);
        }

        // 3. 记录保存成功的日志
        log.info("[KB][QUERY_LOG] 检索轨迹保存成功 - queryLogId={}, traceCount={}",
                queryLogId, searchResults.size());
    }
    
    /**
     * 查询用户知识库问答历史。
     *
     * <p>根据用户 ID 及可选的知识库 ID，按时间倒序查询问答记录列表。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 历史问答列表
     */
    @Override
    public List<KbQueryHistoryResponse> listQueryHistory(Long userId, Long kbId) {
        // 1. 构建查询条件：按用户 ID 等值查询，并按创建时间倒序排列
        LambdaQueryWrapper<KbQueryLog> wrapper = new LambdaQueryWrapper<KbQueryLog>()
                .eq(KbQueryLog::getUserId, userId)
                .orderByDesc(KbQueryLog::getCreatedAt);

        // 2. 若指定了知识库 ID，则追加等值条件
        if (kbId != null) {
            wrapper.eq(KbQueryLog::getKbId, kbId);
        }

        // 3. 执行数据库查询，获取日志列表
        List<KbQueryLog> queryLogs = kbQueryLogMapper.selectList(wrapper);

        // 4. 记录查询成功的日志
        log.info("[KB][QUERY_LOG] 查询问答历史成功 - userId={}, kbId={}, count={}",
                userId, kbId, queryLogs.size());

        // 5. 将日志实体转换为响应 DTO 列表并返回
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

    /**
     * 保存用户对查询结果的反馈。
     *
     * <p>校验参数及用户权限后，将点赞/点踩、评分及反馈内容持久化到查询反馈表。
     *
     * @param userId 当前用户 ID
     * @param queryLogId 查询日志 ID
     * @param request 反馈请求
     */
    @Override
    public void saveFeedback(Long userId, Long queryLogId, QueryFeedbackRequest request) {
        // 1. 校验基础参数有效性
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (queryLogId == null || queryLogId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "queryLogId 不能为空");
        }
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "反馈请求不能为空");
        }

        // 2. 根据 queryLogId 查询对应的查询日志记录
        KbQueryLog queryLog = kbQueryLogMapper.selectById(queryLogId);
        if (queryLog == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "查询记录不存在");
        }

        // 3. 校验当前用户是否为该查询记录的归属人
        if (!userId.equals(queryLog.getUserId())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "无权反馈该查询记录");
        }

        // 4. 构建查询反馈实体
        KbQueryFeedback feedback = KbQueryFeedback.builder()
                .queryLogId(queryLogId)
                .userId(userId)
                .helpful(Boolean.TRUE.equals(request.getHelpful()) ? 1 : 0)
                .rating(request.getRating())
                .feedbackContent(request.getFeedbackContent())
                .createdAt(LocalDateTime.now())
                .build();

        // 5. 将反馈记录写入数据库
        kbQueryFeedbackMapper.insert(feedback);

        // 6. 记录保存成功的日志
        log.info("[KB][QUERY_LOG] 查询反馈保存成功 - queryLogId={}, userId={}, helpful={}, rating={}",
                queryLogId, userId, request.getHelpful(), request.getRating());
    }

    /**
     * 将引用中的 chunkId 列表转换为 JSON 字符串。
     *
     * <p>若引用列表为空，则返回空数组 JSON；序列化异常时降级返回 "[]"。
     *
     * @param citations 引用列表
     * @return JSON 字符串
     */
    private String toCitedChunkIdsJson(List<CitationResponse> citations) {
        // 1. 提取引用列表中的 chunkId，若引用为空则返回空列表
        List<Long> chunkIds = citations == null
                ? List.of()
                : citations.stream().map(CitationResponse::getChunkId).toList();

        // 2. 将 chunkId 列表序列化为 JSON 字符串
        try {
            return objectMapper.writeValueAsString(chunkIds);
        } catch (JsonProcessingException e) {
            // 3. 序列化失败时记录错误日志并返回空数组
            log.error("[KB][QUERY_LOG] 引用 chunkId 序列化失败 - chunkIds={}", chunkIds, e);
            return "[]";
        }
    }
}
