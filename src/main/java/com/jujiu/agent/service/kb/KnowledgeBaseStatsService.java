package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.response.KbStatsOverviewResponse;

/**
 * 知识库统计服务接口。
 *
 * <p>统一负责知识库文档、查询、反馈等统计信息的汇总。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public interface KnowledgeBaseStatsService {
    /**
     * 查询知识库概览统计。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 概览统计信息
     */
    KbStatsOverviewResponse getOverview(Long userId, Long kbId);
}
