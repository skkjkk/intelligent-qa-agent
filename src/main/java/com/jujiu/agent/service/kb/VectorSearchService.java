package com.jujiu.agent.service.kb;

import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.service.kb.model.SearchDebugResult;

import java.util.List;

/**
 * 知识库检索服务接口。
 *
 * <p>负责根据用户问题执行知识库检索，
 * 返回可用于 RAG 上下文构造的分块命中结果列表。
 *
 * <p>当前阶段优先服务最小 RAG 闭环，
 * 首版可先实现最小可用检索，再逐步扩展到混合检索。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
public interface VectorSearchService {
    /**
     * 执行知识库检索。
     *
     * @param kbId 知识库 ID
     * @param userId 当前用户 ID
     * @param question 用户问题
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    List<ChunkSearchResult> search(Long kbId, Long userId, String question, Integer topK);

    /**
     * 执行检索调试。
     *
     * <p>返回检索层中间态结果，不进入模型生成阶段。
     *
     * @param kbId     知识库 ID
     * @param userId   当前用户 ID
     * @param question 用户问题
     * @param topK     最终目标 topK
     * @return 检索调试结果
     */
    SearchDebugResult debugSearch(Long kbId, Long userId, String question, Integer topK);

}
