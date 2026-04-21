package com.jujiu.agent.module.kb.application.service.impl;

import com.jujiu.agent.module.kb.application.model.ChunkSearchResult;
import com.jujiu.agent.module.kb.application.service.RetrievalRerankService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * rerank 关闭时的空实现。
 *
 * <p>这样可以让检索链路始终依赖统一的 RetrievalRerankService，
 * 而不需要在业务层分散判空。
 *
 * @author 17644
 * @since 2026/4/19
 */
@Service
@ConditionalOnMissingBean(RetrievalRerankService.class)
public class NoopRetrievalRerankServiceImpl implements RetrievalRerankService {
    /**
     * 直接返回原候选。
     *
     * @param question   用户问题
     * @param candidates 检索候选
     * @param topN       最终保留数量
     * @return 原候选结果
     */
    @Override
    public List<ChunkSearchResult> rerank(String question, List<ChunkSearchResult> candidates, Integer topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int finalTopN = (topN == null || topN <= 0) ? candidates.size() : topN;
        List<ChunkSearchResult> results = candidates.stream()
                .limit(finalTopN)
                .toList();

        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRank(i + 1);
        }
        return results;
    }
}
