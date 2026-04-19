package com.jujiu.agent.service.kb;

import com.jujiu.agent.common.result.ChunkSearchResult;

import java.util.List;

/**
 * 检索结果 rerank 服务接口。
 *
 * <p>职责边界：
 * <ul>
 *     <li>输入：检索层 raw candidates</li>
 *     <li>输出：按“回答问题能力”重排后的 candidates</li>
 *     <li>不负责 citation / context / organizer</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/19
 */
public interface RetrievalRerankService {

    /**
     * 对候选结果执行 rerank。
     *
     * @param question   用户问题
     * @param candidates 检索候选
     * @param topN       最终保留数量
     * @return rerank 后的候选结果
     */
    List<ChunkSearchResult> rerank(String question, List<ChunkSearchResult> candidates, Integer topN);
}