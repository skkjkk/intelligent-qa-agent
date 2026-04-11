package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.response.KbQueryStatsResponse;

/**
 * 知识库问答统计服务接口。
 *
 * <p>统一负责知识库问答维度统计信息的汇总。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public interface KnowledgeBaseQueryStatsService {
    /**
     * 查询问答统计信息。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 问答统计结果
     */
    KbQueryStatsResponse getQueryStats(Long userId, Long kbId);
}
