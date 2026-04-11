package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.response.KbStatsOverviewResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbQueryFeedback;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.repository.KbQueryFeedbackRepository;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.service.kb.KnowledgeBaseStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 知识库统计服务实现。
 *
 * <p>用于聚合当前用户在指定知识库下的文档、查询与反馈等概览统计信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Service
@Slf4j
public class KnowledgeBaseStatsServiceImpl implements KnowledgeBaseStatsService {

    private final KbDocumentRepository KbDocumentRepository;
    private final KbQueryLogRepository kbQueryLogRepository;
    private final KbQueryFeedbackRepository kbQueryFeedbackRepository;
    public KnowledgeBaseStatsServiceImpl(KbDocumentRepository KbDocumentRepository,
                                         KbQueryLogRepository kbQueryLogRepository,
                                         KbQueryFeedbackRepository kbQueryFeedbackRepository) {
        this.KbDocumentRepository = KbDocumentRepository;
        this.kbQueryLogRepository = kbQueryLogRepository;
        this.kbQueryFeedbackRepository = kbQueryFeedbackRepository;
    }
    /**
     * 查询知识库概览统计。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 概览统计信息
     */
    @Override
    public KbStatsOverviewResponse getOverview(Long userId, Long kbId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        Long totalDocuments = countDocuments(userId, kbId, null);
        Long successDocuments = countDocuments(userId, kbId, "SUCCESS");
        Long processingDocuments = countDocuments(userId, kbId, "PROCESSING");
        Long failedDocuments = countDocuments(userId, kbId, "FAILED");
        
        Long totalQueries = countQueries(userId, kbId, null);
        Long successQueries = countQueries(userId, kbId, "SUCCESS");
        Long totalFeedbacks = countFeedbacks(userId, kbId);
        
        log.info("[KB][STATS] 概览统计查询完成 - userId={}, kbId={}, totalDocuments={}, totalQueries={}, totalFeedbacks={}",
                userId, kbId, totalDocuments, totalQueries, totalFeedbacks);

        return KbStatsOverviewResponse.builder()
                .totalDocuments(totalDocuments)
                .successDocuments(successDocuments)
                .processingDocuments(processingDocuments)
                .failedDocuments(failedDocuments)
                .totalQueries(totalQueries)
                .successQueries(successQueries)
                .totalFeedbacks(totalFeedbacks)
                .build();
    }

    /**
     * 统计反馈数量。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @return 反馈数量
     */
    private Long countFeedbacks(Long userId, Long kbId) {
        if (kbId == null) {
            return kbQueryFeedbackRepository.selectCount(
                    new LambdaQueryWrapper<KbQueryFeedback>()
                            .eq(KbQueryFeedback::getUserId, userId)
            );
        }

        return kbQueryFeedbackRepository.selectCount(
                new LambdaQueryWrapper<KbQueryFeedback>()
                        .inSql(KbQueryFeedback::getQueryLogId,
                                "SELECT id FROM kb_query_log WHERE user_id = " + userId + " AND kb_id = " + kbId)
        );
    }

    /**
     * 统计查询数量。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param status 查询状态，可为空
     * @return 查询数量
     */
    private Long countQueries(Long userId, Long kbId, String status) {
        LambdaQueryWrapper<KbQueryLog> wrapper = new LambdaQueryWrapper<KbQueryLog>()
                .eq(KbQueryLog::getUserId, userId);

        if (kbId != null) {
            wrapper.eq(KbQueryLog::getKbId, kbId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KbQueryLog::getStatus, status);
        }

        return kbQueryLogRepository.selectCount(wrapper);
    }

    /**
     * 统计文档数量。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param status 文档状态，可为空
     * @return 文档数量
     */
    private Long countDocuments(Long userId, Long kbId, String status) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getOwnerUserId, userId)
                .eq(KbDocument::getDeleted, 0);

        if (kbId != null) {
            wrapper.eq(KbDocument::getKbId, kbId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KbDocument::getStatus, status);
        }
        return KbDocumentRepository.selectCount(wrapper);
    }
}
