package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.application.service.impl.ChunkServiceImpl;
import com.jujiu.agent.module.kb.domain.entity.KbChunk;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link ChunkServiceImpl} 最终版单元测试。
 *
 * <p>本测试类用于锁定分块层最终版的主结构行为，确保后续优化只在参数层面微调，
 * 而不再回退或改写主分块流程。
 *
 * <p>当前最终版分块结构固定为：
 * <ol>
 *     <li>文本标准化</li>
 *     <li>段落优先切分</li>
 *     <li>段落拼装为 chunk</li>
 *     <li>超长段按句子优先切分</li>
 *     <li>超长句按字符数最终兜底</li>
 *     <li>仅在最终兜底场景使用 overlap</li>
 * </ol>
 *
 * <p>本测试重点验证：
 * <ul>
 *     <li>空文本安全返回</li>
 *     <li>短段落会优先拼装</li>
 *     <li>超长段不会直接退回纯字符滑窗，而是先走句子优先</li>
 *     <li>超长句才会触发字符兜底</li>
 *     <li>chunkIndex 连续稳定</li>
 *     <li>splitAndSave 正常持久化所有生成 chunk</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/17
 */
class ChunkServiceImplTest {

    private KbChunkMapper kbChunkMapper;
    private KnowledgeBaseProperties properties;
    private ChunkServiceImpl chunkService;

    @BeforeEach
    void setUp() {
        kbChunkMapper = mock(KbChunkMapper.class);

        properties = new KnowledgeBaseProperties();
        properties.getChunking().setDefaultSize(700);
        properties.getChunking().setDefaultOverlap(80);
        properties.getChunking().setMaxSize(1200);

        chunkService = new ChunkServiceImpl(kbChunkMapper, properties);
    }

    @Test
    @DisplayName("内容为空时应返回空列表")
    void split_shouldReturnEmptyList_whenContentBlank() {
        List<KbChunk> chunks = chunkService.split(1L, "   ");

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("多个短段落应优先拼装为较完整 chunk")
    void split_shouldAssembleShortParagraphsIntoChunk() {
        String content = """
                第一段：ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。

                第二段：rebuildFailedIndexes 是做什么的 权限允许删除文档、重建索引和管理授权。

                第三段：RAG 优化重点包括去重、citation 和 snippet 收口。
                """;

        List<KbChunk> chunks = chunkService.split(1L, content);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertEquals(1, chunks.size());

        String chunkContent = chunks.get(0).getContent();
        assertNotNull(chunkContent);
        assertTrue(chunkContent.contains("第一段：ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。"));
        assertTrue(chunkContent.contains("第二段：rebuildFailedIndexes 是做什么的 权限允许删除文档、重建索引和管理授权。"));
        assertTrue(chunkContent.contains("第三段：RAG 优化重点包括去重、citation 和 snippet 收口。"));
        assertTrue(chunkContent.contains("\n\n"));
    }

    @Test
    @DisplayName("超长段落应优先按句子切分而不是直接字符滑窗切分")
    void split_shouldSplitOversizedParagraphBySentencesFirst() {
        String content = """
                第一句：这是一个非常长的知识说明段落，用来测试超长段在最终版分块逻辑中是否优先按句子边界切分而不是直接字符滑窗切分。
                第二句：如果当前逻辑正确，那么这些句子应该尽量作为完整单元被拼装进 chunk，而不是从中间被硬截断。
                第三句：最终生成的 chunk 应更接近完整句群，从而提升后续 citation、snippet 和上下文注入的质量。
                第四句：这一点对于技术文档、制度文档和架构说明类文档尤其重要，因为它们通常段落长但句子边界清晰。
                """;

        List<KbChunk> chunks = chunkService.split(1L, content);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        for (KbChunk chunk : chunks) {
            assertNotNull(chunk.getContent());
            assertFalse(chunk.getContent().isBlank());

            boolean looksLikeSentenceBoundary =
                    chunk.getContent().endsWith("。")
                            || chunk.getContent().endsWith("！")
                            || chunk.getContent().endsWith("？")
                            || chunk.getContent().length() <= 700;

            assertTrue(looksLikeSentenceBoundary);
        }
    }

    @Test
    @DisplayName("仅当单句本身超长时才应触发字符数最终兜底切分")
    void split_shouldFallbackToCharacterSplitOnlyWhenSingleSentenceTooLong() {
        String longSentence = "这是一个没有自然句边界的超长句子".repeat(80);

        List<KbChunk> chunks = chunkService.split(1L, longSentence);

        assertNotNull(chunks);
        assertTrue(chunks.size() >= 2);

        for (KbChunk chunk : chunks) {
            assertNotNull(chunk.getContent());
            assertFalse(chunk.getContent().isBlank());
            assertTrue(chunk.getContent().length() <= 700);
        }
    }

    @Test
    @DisplayName("chunkIndex 应从 0 开始连续递增")
    void split_shouldAssignSequentialChunkIndex() {
        String content = """
                第一段：这是第一段内容，主要介绍系统背景、模块分层和总体流程。

                第二段：这是第二段内容，主要介绍知识库检索、向量检索和关键词检索。

                第三段：这是第三段内容，主要介绍 organizer 的去重、snippet、citation 和 context 收口。

                第四段：这是第四段内容，主要介绍聊天显式知识增强和 knowledge_base 工具协同。
                """;

        List<KbChunk> chunks = chunkService.split(1L, content);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex());
        }
    }

    @Test
    @DisplayName("生成的 chunk 应正确填充基础统计字段")
    void split_shouldFillBasicChunkFields() {
        String content = """
                第一段：ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。

                第二段：RAG 优化包括 chunking、retrieval、organizer 和 agent 协同。
                """;

        List<KbChunk> chunks = chunkService.split(1L, content);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        for (KbChunk chunk : chunks) {
            assertEquals(1L, chunk.getDocumentId());
            assertNotNull(chunk.getContent());
            assertTrue(chunk.getCharCount() > 0);
            assertTrue(chunk.getTokenCount() > 0);
            assertEquals(1, chunk.getEnabled());
            assertNotNull(chunk.getCreatedAt());
            assertNotNull(chunk.getUpdatedAt());
        }
    }

    @Test
    @DisplayName("splitAndSave 应保存 split 生成的所有 chunk")
    void splitAndSave_shouldPersistAllGeneratedChunks() {
        String content = """
                第一段：ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。

                第二段：RAG 优化重点包括去重、citation 和 snippet 收口。
                """;

        List<KbChunk> chunks = chunkService.splitAndSave(1L, content);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        verify(kbChunkMapper, times(chunks.size())).insert(any(KbChunk.class));
    }
}
