package com.jujiu.agent.module.kb.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.api.response.KbDimensionCountResponse;
import com.jujiu.agent.module.kb.api.response.KbQueryStatsResponse;
import com.jujiu.agent.module.kb.api.response.KbTrendPointResponse;
import com.jujiu.agent.module.kb.domain.entity.KbQueryLog;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryFeedbackMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryLogMapper;
import com.jujiu.agent.module.kb.application.service.KnowledgeBaseQueryStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库问答统计服务实现。
 *
 * <p>用于统计当前用户在指定知识库下的问答状态分布、
 * 平均耗时与平均 Token 消耗等信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Service
@Slf4j
public class KnowledgeBaseQueryStatsServiceImpl implements KnowledgeBaseQueryStatsService {
    /** 知识库查询日志仓储。 */
    private final KbQueryLogMapper kbQueryLogMapper;
    /** 知识库查询反馈仓储。 */
    private final KbQueryFeedbackMapper kbQueryFeedbackMapper;

    /**
     * 构造方法。
     *
     * @param kbQueryLogMapper 知识库查询日志仓储
     * @param kbQueryFeedbackMapper 知识库查询反馈仓储
     */
    public KnowledgeBaseQueryStatsServiceImpl(KbQueryLogMapper kbQueryLogMapper,
                                              KbQueryFeedbackMapper kbQueryFeedbackMapper) {
        this.kbQueryLogMapper = kbQueryLogMapper;
        this.kbQueryFeedbackMapper = kbQueryFeedbackMapper;
    }
    
    /**
     * 查询问答统计信息
     * 
     * <p>查询并汇总知识库的问答统计数据，提供多维度的统计信息。
     * 包括问答数量、质量反馈、平均耗时、平均Token消耗、评分分布、趋势分析等。
     * 
     * <p>统计维度：
     * <ul>
     *     <li>问答数量统计：总数、成功数、空结果数、失败数</li>
     *     <li>性能统计：平均响应延迟、平均Token消耗</li>
     *     <li>质量反馈：有帮助数、无帮助数、平均评分</li>
     *     <li>评分分布：按评分等级分布统计</li>
     *     <li>趋势统计：7天趋势、30天趋势</li>
     * </ul>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>参数校验：验证userId有效性</li>
     *     <li>统计问答摘要：查询日志聚合、反馈聚合、评分分布</li>
     *     <li>统计趋势数据：查询7天和30天趋势</li>
     *     <li>统计问答数量：按状态分别统计</li>
     *     <li>构建并返回响应对象</li>
     * </ol>
     * 
     * <p>数据来源：
     * <ul>
     *     <li>问答日志表 (kb_query_log) - 记录每次问答请求</li>
     *     <li>问答反馈表 (kb_query_feedback) - 记录用户反馈</li>
     * </ul>
     *
     * @param userId 当前用户 ID，必填
     * @param kbId 知识库 ID，可选，为空时统计用户所有知识库
     * @return 问答统计结果，包含多维度统计数据
     */
    @Override
    public KbQueryStatsResponse getQueryStats(Long userId, Long kbId, Integer windowDays, ZoneId zoneId, Integer topN) {
        // 1. 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }

        // 2. 统计问答摘要
        Map<String, Object> summary = kbQueryLogMapper.aggregateSummary(userId, kbId);
        Map<String, Object> feedback = kbQueryFeedbackMapper.aggregateQuality(userId, kbId);
        List<Map<String, Object>> ratingRows = kbQueryFeedbackMapper.aggregateRatingDistribution(userId, kbId);

        // 3. 统计趋势数据
        LocalDateTime now = LocalDateTime.now();
        List<KbTrendPointResponse> trend7Days = toTrend(kbQueryLogMapper.aggregateTrend(userId, kbId, now.minusDays(7)));
        List<KbTrendPointResponse> trend30Days = toTrend(kbQueryLogMapper.aggregateTrend(userId, kbId, now.minusDays(30)));
        List<KbDimensionCountResponse> ratingDistribution = toDimension(ratingRows);
        
        // 4. 统计问答数量
        Long totalQueries = countQueries(userId, kbId, null);
        Long successQueries = countQueries(userId, kbId, "SUCCESS");
        Long emptyQueries = countQueries(userId, kbId, "EMPTY");
        Long failedQueries = countQueries(userId, kbId, "FAILED");
        

        log.info("[KB][STATS] 问答统计查询完成 - userId={}, kbId={}, totalQueries={}, successQueries={}, emptyQueries={}, failedQueries={}",
                userId, kbId, totalQueries, successQueries, emptyQueries, failedQueries);

        // 4. 构建并返回响应对象
        return KbQueryStatsResponse.builder()
                .totalQueries(longVal(summary.get("totalQueries")))
                .successQueries(longVal(summary.get("successQueries")))
                .emptyQueries(longVal(summary.get("emptyQueries")))
                .failedQueries(longVal(summary.get("failedQueries")))
                .avgLatencyMs(longVal(summary.get("avgLatencyMs")))
                .avgTotalTokens(longVal(summary.get("avgTotalTokens")))
                .helpfulCount(longVal(feedback.get("helpfulCount")))
                .unhelpfulCount(longVal(feedback.get("unhelpfulCount")))
                .avgRating(doubleVal(feedback.get("avgRating")))
                .ratingDistribution(ratingDistribution)
                .trend7Days(trend7Days)
                .trend30Days(trend30Days)
                .build();
    }

    /**
     * 将数据库查询结果转换为趋势数据响应列表
     * 
     * <p>将SQL查询返回的Map列表转换为业务响应的DTO列表。
     * 这是一个数据转换方法，将数据库层面的数据格式转换为API响应格式。
     * <p>输入数据格式（来自SQL查询）：
     * <pre>
     * [
     *     {"dayVal": "2026-04-11", "dayCount": 100},
     *     {"dayVal": "2026-04-12", "dayCount": 200},
     *     {"dayVal": "2026-04-13", "dayCount": 300}
     * ]
     * </pre>
     * 
     * <p>输出数据格式（转换为DTO）：
     * <pre>
     * [
     *     {"day": "2026-04-11", "count": 100},
     *     {"day": "2026-04-12", "count": 200},
     *     {"day": "2026-04-13", "count": 300}
     * </pre>
     *
     * @param rows 从数据库查询返回的Map列表
     * @return 趋势数据响应列表
     */
    private List<KbTrendPointResponse> toTrend(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> KbTrendPointResponse.builder()
                .day(String.valueOf(r.get("dayVal")))
                .count(longVal(r.get("dayCount")))
                .build()).collect(Collectors.toList());
    }

    /**
     * 将数据库查询结果转换为维度统计响应列表
     * 
     * <p>将SQL查询返回的Map列表转换为业务响应的DTO列表。
     * 这是一个数据转换方法，将数据库层面的数据格式转换为API响应格式。
     * <p>输入数据格式（来自SQL查询）：
     * <pre>
     * [
     *     {"dimName": "状态", "dimCount": 100},
     *     {"dimName": "状态", "dimCount": 200},
     *     {"dimName": "状态", "dimCount": 300}
     * ]
     * </pre>
     * 
     * <p>输出数据格式（转换为DTO）：
     * <pre>
     * [
     *     {"name": "状态", "count": 100},
     *     {"name": "状态", "count": 200},
     *     {"name": "状态", "count": 300}
     * ]
     * </pre>
     *
     * @param rows 从数据库查询返回的Map列表
     */
    private List<KbDimensionCountResponse> toDimension(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> KbDimensionCountResponse.builder()
                .name(String.valueOf(r.get("dimName")))
                .count(longVal(r.get("dimCount")))
                .build()).collect(Collectors.toList());
    }
    
    /**
     * 安全转换对象为长整数。
     * 
     * @param v 待转换的对象
     * @return 转换后的长整数，0 表示转换失败
     */
    private Long longVal(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    /**
     * 安全转换对象为双精度浮点数。
     * 
     * @param v 待转换的对象
     * @return 转换后的双精度浮点数，0 表示转换失败
     */
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
    
    /**
     * 统计问答数量。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param status 查询状态，可为空
     * @return 问答数量
     */
    private Long countQueries(Long userId, Long kbId, String status) {
        // 1. 构建基础查询条件：按用户 ID 过滤
        LambdaQueryWrapper<KbQueryLog> wrapper = new LambdaQueryWrapper<KbQueryLog>()
                .eq(KbQueryLog::getUserId, userId);

        // 2. 按需追加知识库 ID 和状态筛选条件
        if (kbId != null) {
            wrapper.eq(KbQueryLog::getKbId, kbId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KbQueryLog::getStatus, status);
        }

        // 3. 执行计数查询
        return kbQueryLogMapper.selectCount(wrapper);
    }
}