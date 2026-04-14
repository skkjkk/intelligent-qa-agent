package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.response.KbQueryStatsResponse;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.service.kb.KnowledgeBaseQueryStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final KbQueryLogRepository kbQueryLogRepository;

    /**
     * 构造方法。
     *
     * @param kbQueryLogRepository 知识库查询日志仓储
     */
    public KnowledgeBaseQueryStatsServiceImpl(KbQueryLogRepository kbQueryLogRepository) {
        this.kbQueryLogRepository = kbQueryLogRepository;
    }
    
    /**
     * 查询问答统计信息。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 问答统计结果
     */
    @Override
    public KbQueryStatsResponse getQueryStats(Long userId, Long kbId) {
        // 1. 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }

        // 2. 统计各状态问答数量
        Long totalQueries = countQueries(userId, kbId, null);
        Long successQueries = countQueries(userId, kbId, "SUCCESS");
        Long emptyQueries = countQueries(userId, kbId, "EMPTY");
        Long failedQueries = countQueries(userId, kbId, "FAILED");

        // 3. 查询日志列表并计算平均值
        List<KbQueryLog> queryLogs = listQueries(userId, kbId);
        Long avgLatencyMs = calculateAverageLatency(queryLogs);
        Long avgTotalTokens = calculateAverageTokens(queryLogs);

        log.info("[KB][STATS] 问答统计查询完成 - userId={}, kbId={}, totalQueries={}, successQueries={}, emptyQueries={}, failedQueries={}",
                userId, kbId, totalQueries, successQueries, emptyQueries, failedQueries);

        // 4. 构建并返回响应对象
        return KbQueryStatsResponse.builder()
                .totalQueries(totalQueries)
                .successQueries(successQueries)
                .emptyQueries(emptyQueries)
                .failedQueries(failedQueries)
                .avgLatencyMs(avgLatencyMs)
                .avgTotalTokens(avgTotalTokens)
                .build();
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
        return kbQueryLogRepository.selectCount(wrapper);
    }

    /**
     * 查询问答日志列表。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @return 问答日志列表
     */
    private List<KbQueryLog> listQueries(Long userId, Long kbId) {
        // 1. 构建基础查询条件
        LambdaQueryWrapper<KbQueryLog> wrapper = new LambdaQueryWrapper<KbQueryLog>()
                .eq(KbQueryLog::getUserId, userId);

        // 2. 若指定了知识库 ID，追加筛选条件
        if (kbId != null) {
            wrapper.eq(KbQueryLog::getKbId, kbId);
        }

        // 3. 执行查询
        return kbQueryLogRepository.selectList(wrapper);
    }

    /**
     * 计算平均耗时。
     *
     * @param queryLogs 问答日志列表
     * @return 平均耗时
     */
    private Long calculateAverageLatency(List<KbQueryLog> queryLogs) {
        // 1. 若日志为空，直接返回 0
        if (queryLogs == null || queryLogs.isEmpty()) {
            return 0L;
        }

        // 2. 累加所有有效的 latencyMs
        long totalLatency = 0L;
        int count = 0;
        for (KbQueryLog queryLog : queryLogs) {
            Integer latencyMs = queryLog.getLatencyMs();
            if (latencyMs != null && latencyMs >= 0) {
                totalLatency += latencyMs;
                count++;
            }
        }

        // 3. 计算平均值并返回
        return count == 0 ? 0L : totalLatency / count;
    }

    /**
     * 计算平均总 Token 数。
     *
     * @param queryLogs 问答日志列表
     * @return 平均 Token 数
     */
    private Long calculateAverageTokens(List<KbQueryLog> queryLogs) {
        // 1. 若日志为空，直接返回 0
        if (queryLogs == null || queryLogs.isEmpty()) {
            return 0L;
        }

        // 2. 累加所有有效的 totalTokens
        long totalTokens = 0L;
        int count = 0;
        for (KbQueryLog queryLog : queryLogs) {
            Integer tokens = queryLog.getTotalTokens();
            if (tokens != null && tokens >= 0) {
                totalTokens += tokens;
                count++;
            }
        }

        // 3. 计算平均值并返回
        return count == 0 ? 0L : totalTokens / count;
    }
}
