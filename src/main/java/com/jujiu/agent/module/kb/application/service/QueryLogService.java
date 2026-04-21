package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.chat.infrastructure.llm.LlmResult;
import com.jujiu.agent.module.kb.application.model.ChunkSearchResult;
import com.jujiu.agent.module.kb.api.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.module.kb.api.response.CitationResponse;
import com.jujiu.agent.module.kb.api.response.KbQueryHistoryResponse;
import com.jujiu.agent.module.kb.domain.entity.KbQueryLog;
import com.jujiu.agent.module.kb.api.request.QueryFeedbackRequest;

import java.util.List;

/**
 * 知识库查询日志服务接口。
 *
 * <p>统一负责知识库问答日志、检索轨迹以及后续反馈数据的管理。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public interface QueryLogService {
    /**
     * 保存知识库问答日志。
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
    KbQueryLog saveQueryLog(Long userId,
                            Long kbId,
                            QueryKnowledgeBaseRequest request,
                            Integer topK,
                            LlmResult llmResult,
                            List<CitationResponse> citations,
                            long latencyMs,
                            String status,
                            String errorMessage);

    /**
     * 保存检索轨迹。
     *
     * @param queryLogId 查询日志 ID
     * @param searchResults 检索结果
     */
    void saveRetrievalTrace(Long queryLogId, List<ChunkSearchResult> searchResults);

    /**
     * 查询用户知识库问答历史。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 历史问答列表
     */
    List<KbQueryHistoryResponse> listQueryHistory(Long userId, Long kbId);

    /**
     * 保存知识库问答反馈。
     *
     * @param userId 当前用户 ID
     * @param queryLogId 查询日志 ID
     * @param request 反馈请求
     */
    void saveFeedback(Long userId, Long queryLogId, QueryFeedbackRequest request);
}
