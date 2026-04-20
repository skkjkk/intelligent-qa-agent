package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.response.KbDocumentStatsResponse;

import java.time.ZoneId;

/**
 * 知识库文档统计服务接口。
 *
 * <p>统一负责知识库文档维度的统计信息汇总。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public interface KnowledgeBaseDocumentStatsService {
    /**
     * 查询文档统计信息。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 文档统计结果
     */
    KbDocumentStatsResponse getDocumentStats(Long userId, Long kbId, Integer windowDays, ZoneId zoneId, Integer topN);
}
