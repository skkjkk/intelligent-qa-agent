package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.service.kb.model.OrganizedRetrievalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultRetrievalResultOrganizer} 单元测试。
 *
 * <p>该测试类专门用于锁定“检索结果整理层”的基础行为，
 * 避免后续在优化 snippet、去重、文档裁剪、context 组装时发生回归。
 *
 * <p>第一阶段重点验证：
 * <ul>
 *     <li>空结果统一返回空结构</li>
 *     <li>重复 chunk 会被去重</li>
 *     <li>单文档 chunk 数量会被限制</li>
 *     <li>citation 与 context 来自同一批最终结果</li>
 *     <li>过长内容会被统一截断</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/17
 */
class DefaultRetrievalResultOrganizerTest {

    /**
     * 被测对象。
     */
    private final DefaultRetrievalResultOrganizer organizer = new DefaultRetrievalResultOrganizer();

    @Test
    @DisplayName("原始检索结果为空时应返回统一空结构")
    void organize_shouldReturnEmptyStructure_whenRawResultsEmpty() {
        // 1. 执行整理逻辑，输入空列表。
        OrganizedRetrievalResult result = organizer.organize(List.of(), "ACL 是什么");

        // 2. 校验统一空结构，确保上层链路不需要各自重复判空和兜底。
        assertNotNull(result);
        assertNotNull(result.getFinalResults());
        assertNotNull(result.getCitations());
        assertEquals("", result.getContext());
        assertTrue(result.getFinalResults().isEmpty());
        assertTrue(result.getCitations().isEmpty());
        assertEquals(0, result.getRawResultCount());
        assertEquals(0, result.getFinalResultCount());
        assertEquals("NO_RAW_RESULTS", result.getEmptyReason());
    }

    @Test
    @DisplayName("相同 chunkId 的结果应只保留一条")
    void organize_shouldDeduplicateByChunkId_whenDuplicateChunkExists() {
        // 1. 构造两个 chunkId 相同的候选结果，模拟融合后重复命中的场景。
        ChunkSearchResult first = buildResult(
                11L,
                101L,
                "ACL 设计文档",
                "ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。",
                0.95D,
                1
        );

        ChunkSearchResult duplicate = buildResult(
                11L,
                101L,
                "ACL 设计文档",
                "ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。",
                0.90D,
                2
        );

        // 2. 执行整理逻辑。
        OrganizedRetrievalResult result = organizer.organize(List.of(first, duplicate), "ACL 有哪些权限");

        // 3. 校验重复 chunk 被去重，只保留一条最终结果。
        assertNotNull(result);
        assertEquals(2, result.getRawResultCount());
        assertEquals(1, result.getFinalResultCount());
        assertEquals(1, result.getFinalResults().size());
        assertEquals(1, result.getCitations().size());
        assertEquals("NONE", result.getEmptyReason());

        // 4. 进一步确认保留的是 chunkId=11 的唯一结果。
        assertEquals(11L, result.getFinalResults().get(0).getChunkId());
        assertEquals(11L, result.getCitations().get(0).getChunkId());
    }

    @Test
    @DisplayName("同一文档命中过多时应限制每文档最大保留数量")
    void organize_shouldLimitChunksPerDocument_whenSameDocumentHasTooManyChunks() {
        // 1. 构造同一文档下 3 个不同 chunk，当前第一阶段策略应最多保留 2 个。
        ChunkSearchResult first = buildResult(
                11L,
                101L,
                "ACL 设计文档",
                "第一段内容",
                0.99D,
                1
        );

        ChunkSearchResult second = buildResult(
                12L,
                101L,
                "ACL 设计文档",
                "第二段内容",
                0.98D,
                2
        );

        ChunkSearchResult third = buildResult(
                13L,
                101L,
                "ACL 设计文档",
                "第三段内容",
                0.97D,
                3
        );

        // 2. 执行整理逻辑。
        OrganizedRetrievalResult result = organizer.organize(List.of(first, second, third), "ACL 设计");

        // 3. 校验整理后最多保留 2 条，避免单文档霸榜。
        assertNotNull(result);
        assertEquals(3, result.getRawResultCount());
        assertEquals(2, result.getFinalResultCount());
        assertEquals(2, result.getFinalResults().size());
        assertEquals(2, result.getCitations().size());

        // 4. 确认被保留的两个 chunk 都来自同一文档，且数量被限制住。
        long document101Count = result.getFinalResults().stream()
                .filter(item -> item.getDocumentId().equals(101L))
                .count();
        assertEquals(2, document101Count);
    }

    @Test
    @DisplayName("整理后的 citation 与 context 应来自同一批最终结果")
    void organize_shouldKeepCitationAndContextConsistent_withFinalResults() {
        // 1. 构造两个不同文档的结果，验证最终 context 和 citation 使用统一数据源。
        ChunkSearchResult first = buildResult(
                11L,
                101L,
                "ACL 设计文档",
                "ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。",
                0.95D,
                1
        );

        ChunkSearchResult second = buildResult(
                21L,
                202L,
                "RAG 优化文档",
                "RAG 优化重点包括去重、snippet、citation 与 token 控制。",
                0.90D,
                2
        );

        // 2. 执行整理逻辑。
        OrganizedRetrievalResult result = organizer.organize(List.of(first, second), "如何优化 RAG");

        // 3. 校验结果数量一致，确保 citation / context / finalResults 口径一致。
        assertNotNull(result);
        assertEquals(2, result.getFinalResults().size());
        assertEquals(2, result.getCitations().size());

        // 4. 校验 context 中包含最终文档标题，证明上下文来自最终结果而不是另一套逻辑。
        assertTrue(result.getContext().contains("[1]"));
        assertTrue(result.getContext().contains("ACL 设计文档"));
        assertTrue(result.getContext().contains("RAG 优化文档"));

        // 5. 校验 citation 顺序与 finalResults 的 rank 一致。
        CitationResponse firstCitation = result.getCitations().get(0);
        CitationResponse secondCitation = result.getCitations().get(1);

        assertEquals(result.getFinalResults().get(0).getChunkId(), firstCitation.getChunkId());
        assertEquals(result.getFinalResults().get(1).getChunkId(), secondCitation.getChunkId());
        assertEquals(result.getFinalResults().get(0).getRank(), firstCitation.getRank());
        assertEquals(result.getFinalResults().get(1).getRank(), secondCitation.getRank());
    }

    @Test
    @DisplayName("过长内容应统一截断为受控 snippet")
    void organize_shouldBuildTruncatedSnippet_whenContentTooLong() {
        // 1. 构造超长内容，模拟 chunk 原文过长导致 citation/context 过胖的场景。
        String longContent = "A".repeat(260);

        ChunkSearchResult resultItem = buildResult(
                11L,
                101L,
                "长文档",
                longContent,
                0.95D,
                1
        );

        // 2. 执行整理逻辑。
        OrganizedRetrievalResult result = organizer.organize(List.of(resultItem), "测试长文本");

        // 3. 校验 snippet 被截断，并以省略号结尾。
        assertNotNull(result);
        assertEquals(1, result.getCitations().size());

        String snippet = result.getCitations().get(0).getSnippet();
        assertNotNull(snippet);
        assertTrue(snippet.length() <= 223);
        assertTrue(snippet.endsWith("..."));

        // 4. 同时确认 context 中也是统一后的截断内容，而不是原始长文本。
        assertTrue(result.getContext().contains(snippet));
        assertFalse(result.getContext().contains(longContent));
    }

    /**
     * 构造测试用检索结果。
     *
     * @param chunkId       chunk ID
     * @param documentId    文档 ID
     * @param documentTitle 文档标题
     * @param content       内容
     * @param score         分数
     * @param rank          排名
     * @return 检索结果
     */
    private ChunkSearchResult buildResult(Long chunkId,
                                          Long documentId,
                                          String documentTitle,
                                          String content,
                                          Double score,
                                          Integer rank) {
        return ChunkSearchResult.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .documentTitle(documentTitle)
                .content(content)
                .score(score)
                .rank(rank)
                .build();
    }
}
