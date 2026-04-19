package com.jujiu.agent.service.kb;

import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.service.kb.model.OrganizedRetrievalResult;

import java.util.List;

/**
 * 检索结果整理器。
 *
 * <p>该接口定义“原始检索结果整理层”的统一入口，
 * 用于把向量检索 / 关键词检索 / 混合检索产生的原始候选结果，
 * 统一整理为可直接用于：
 * <ul>
 *     <li>RAG Prompt 上下文</li>
 *     <li>citation 引用展示</li>
 *     <li>知识增强上下文注入</li>
 *     <li>后续检索诊断与 trace 记录</li>
 * </ul>
 *
 * <p>第一阶段先收口以下能力：
 * <ul>
 *     <li>基础去重</li>
 *     <li>单文档 chunk 数量限制</li>
 *     <li>统一 snippet 生成</li>
 *     <li>统一 citation 生成</li>
 *     <li>统一 context 生成</li>
 * </ul>
 *
 * <p>后续若要演进：
 * <ul>
 *     <li>近重复合并</li>
 *     <li>相邻 chunk 合并</li>
 *     <li>query-aware snippet</li>
 *     <li>rerank</li>
 *     <li>空结果诊断</li>
 * </ul>
 * 应继续扩展该接口的实现，而不是把逻辑重新分散回 RagService。
 *
 * @author 17644
 * @since 2026/4/17
 */
public interface RetrievalResultOrganizer {
    
    /**
     * 对原始检索结果执行统一整理。
     *
     * @param rawResults 原始检索结果
     * @param question   用户原始问题，用于后续 snippet 截取和 query-aware 扩展
     * @return 整理后的统一结果对象
     */
    OrganizedRetrievalResult organize(List<ChunkSearchResult> rawResults, String question);
}
