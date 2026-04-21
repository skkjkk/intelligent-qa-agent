package com.jujiu.agent.module.kb.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.api.response.KbStatsOverviewResponse;
import com.jujiu.agent.module.kb.api.response.KbTrendPointResponse;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryFeedbackMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryLogMapper;
import com.jujiu.agent.module.kb.application.service.KnowledgeBaseStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 知识库文档仓储。 */
    private final KbDocumentMapper kbDocumentMapper;
    /** 知识库查询日志仓储。 */
    private final KbQueryLogMapper kbQueryLogMapper;
    /** 知识库查询反馈仓储。 */
    private final KbQueryFeedbackMapper kbQueryFeedbackMapper;

    /**
     * 构造方法。
     *
     * @param kbDocumentMapper       知识库文档仓储
     * @param kbQueryLogMapper       知识库查询日志仓储
     * @param kbQueryFeedbackMapper  知识库查询反馈仓储
     */
    public KnowledgeBaseStatsServiceImpl(KbDocumentMapper kbDocumentMapper,
                                         KbQueryLogMapper kbQueryLogMapper,
                                         KbQueryFeedbackMapper kbQueryFeedbackMapper) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbQueryLogMapper = kbQueryLogMapper;
        this.kbQueryFeedbackMapper = kbQueryFeedbackMapper;
    }

    /**
     * 查询知识库概览统计
     * 
     * <p>查询知识库的综合性概览数据，整合文档、问答、反馈等多维度统计信息。
     * 提供一个接口获取所有核心指标，用于Dashboard展示或数据概览页面。
     * 
     * <p>统计维度：
     * <ul>
     *     <li>文档统计：总数、成功数、处理中数、失败数</li>
     *     <li>问答统计：总数、成功数</li>
     *     <li>反馈统计：总数、有帮助数、无帮助数、平均评分</li>
     *     <li>时间趋势：7天/30天文档创建量、7天/30天问答量</li>
     *     <li>趋势图表：30天问答趋势、30天文档创建趋势</li>
     * </ul>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>参数校验：验证userId有效性</li>
     *     <li>统计文档数量：按状态分别统计</li>
     *     <li>统计查询数量：从聚合摘要中提取</li>
     *     <li>统计反馈数量：从质量聚合中提取</li>
     *     <li>统计时间趋势：7天和30天增量</li>
     *     <li>查询趋势图表数据</li>
     *     <li>构建并返回概览响应</li>
     * </ol>
     * 
     * <p>对比其他统计方法：
     * <ul>
     *     <li>getOverview: 综合概览，一次获取所有核心指标</li>
     *     <li>getDocumentStats: 文档详细统计，多维度分布</li>
     *     <li>getQueryStats: 问答详细统计，质量反馈</li>
     * </ul>
     *
     * @param userId 当前用户 ID，必填
     * @param kbId 知识库 ID，可选，为空时统计用户所有知识库
     * @return 概览统计信息，包含文档、问答、反馈等核心指标
     */
    @Override
    public KbStatsOverviewResponse getOverview(Long userId, Long kbId, Integer windowDays, ZoneId zoneId, Integer topN) {
        // 1. 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }

        // 2. 统计文档数量（总/成功/处理中/失败）
        Long totalDocuments = countDocuments(userId, kbId, null);
        Long successDocuments = countDocuments(userId, kbId, "SUCCESS");
        Long processingDocuments = countDocuments(userId, kbId, "PROCESSING");
        Long failedDocuments = countDocuments(userId, kbId, "FAILED");

        // 3. 统计查询数量（总/成功）
        Map<String, Object> querySummary = kbQueryLogMapper.aggregateSummary(userId, kbId);
        Long totalQueries = longVal(querySummary.get("totalQueries"));
        Long successQueries = longVal(querySummary.get("successQueries"));
        
        // 4. 统计反馈数量（总/成功/helpful/unhelpful）
        Map<String, Object> feedbackQuality = kbQueryFeedbackMapper.aggregateQuality(userId, kbId);
        Long totalFeedbacks = longVal(feedbackQuality.get("totalFeedbacks"));
        Long helpfulCount = longVal(feedbackQuality.get("helpfulCount"));
        Long unhelpfulCount = longVal(feedbackQuality.get("unhelpfulCount"));
        Double avgRating = doubleVal(feedbackQuality.get("avgRating"));

        // 5. 统计文档创建趋势（最近7天/30天）
        LocalDateTime now = LocalDateTime.now();
        Long documentsLast7Days = kbDocumentMapper.countCreatedSince(userId, kbId, now.minusDays(7));
        Long documentsLast30Days = kbDocumentMapper.countCreatedSince(userId, kbId, now.minusDays(30));
        Long queriesLast7Days = kbQueryLogMapper.countSince(userId, kbId, now.minusDays(7));
        Long queriesLast30Days = kbQueryLogMapper.countSince(userId, kbId, now.minusDays(30));

        // 6. 统计查询趋势（最近7天/30天）
        List<KbTrendPointResponse> queryTrend30Days = toTrend(kbQueryLogMapper.aggregateTrend(userId, kbId, now.minusDays(30)));
        List<KbTrendPointResponse> documentTrend30Days = toTrend(kbDocumentMapper.aggregateCreatedTrend(userId, kbId, now.minusDays(30)));
        
      log.info("[KB][STATS] 概览统计查询完成 - userId={}, kbId={}, totalDocuments={}, totalQueries={}, totalFeedbacks={}",
                userId, kbId, totalDocuments, totalQueries, totalFeedbacks);

        // 7. 构建并返回概览响应
        return KbStatsOverviewResponse.builder()
                .totalDocuments(totalDocuments)
                .successDocuments(successDocuments)
                .processingDocuments(processingDocuments)
                .failedDocuments(failedDocuments)
                .totalQueries(totalQueries)
                .successQueries(successQueries)
                .totalFeedbacks(totalFeedbacks)
                .documentsLast7Days(documentsLast7Days)
                .documentsLast30Days(documentsLast30Days)
                .queriesLast7Days(queriesLast7Days)
                .queriesLast30Days(queriesLast30Days)
                .helpfulCount(helpfulCount)
                .unhelpfulCount(unhelpfulCount)
                .avgRating(avgRating)
                .queryTrend30Days(queryTrend30Days)
                .documentTrend30Days(documentTrend30Days)
                .build();
    }

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
        return kbDocumentMapper.selectCount(wrapper);
    }

    private List<KbTrendPointResponse> toTrend(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> KbTrendPointResponse.builder()
                .day(String.valueOf(row.get("dayVal")))
                .count(longVal(row.get("dayCount")))
                .build()).collect(Collectors.toList());
    }

    private Long longVal(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    private Double doubleVal(Object v) {
        if (v == null) {
            return 0D;
        }
        if (v instanceof BigDecimal b) {
            return b.doubleValue();
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(v));
    }
}