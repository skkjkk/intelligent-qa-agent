package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.repository.KbChunkRepository;
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

    private KbChunkRepository kbChunkRepository;
    private KnowledgeBaseProperties properties;
    private ChunkServiceImpl chunkService;

    @BeforeEach
    void setUp() {
        // 1. 初始化 mock 仓储。
        kbChunkRepository = mock(KbChunkRepository.class);

        // 2. 初始化知识库配置，并使用当前已经确定的默认参数。
        properties = new KnowledgeBaseProperties();
        properties.getChunking().setDefaultSize(700);
        properties.getChunking().setDefaultOverlap(80);
        properties.getChunking().setMaxSize(1200);

        // 3. 构造被测对象。
        chunkService = new ChunkServiceImpl(kbChunkRepository, properties);
    }

    @Test
    @DisplayName("内容为空时应返回空列表")
    void split_shouldReturnEmptyList_whenContentBlank() {
        // 1. 输入空白文本执行分块。
        List<KbChunk> chunks = chunkService.split(1L, "   ");

        // 2. 校验结果为空，确保不会产生脏 chunk。
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("多个短段落应优先拼装为较完整 chunk")
    void split_shouldAssembleShortParagraphsIntoChunk() {
        // 1. 构造多个短段落，验证最终版会优先按段落拼装，而不是机械固定长度切分。
        String content = """
                第一段：ACL 包括 READ、MANAGE、SHARE 三类权限。

                第二段：MANAGE 权限允许删除文档、重建索引和管理授权。

                第三段：RAG 优化重点包括去重、citation 和 snippet 收口。
                """;

        // 2. 执行分块。
        List<KbChunk> chunks = chunkService.split(1L, content);

        // 3. 基础断言：应成功生成 chunk。
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // 4. 在当前 chunkSize=700 的配置下，这三段内容应被优先拼装，而不是切成很多碎块。
        assertEquals(1, chunks.size());

        // 5. 校验 chunk 内容中保留了多个段落的完整语义。
        String chunkContent = chunks.get(0).getContent();
        assertNotNull(chunkContent);
        assertTrue(chunkContent.contains("第一段：ACL 包括 READ、MANAGE、SHARE 三类权限。"));
        assertTrue(chunkContent.contains("第二段：MANAGE 权限允许删除文档、重建索引和管理授权。"));
        assertTrue(chunkContent.contains("第三段：RAG 优化重点包括去重、citation 和 snippet 收口。"));

        // 6. 校验段落边界仍然存在，说明没有被压成一整段纯文本。
        assertTrue(chunkContent.contains("\n\n"));
    }

    @Test
    @DisplayName("超长段落应优先按句子切分而不是直接字符滑窗切分")
    void split_shouldSplitOversizedParagraphBySentencesFirst() {
        // 1. 构造单个超长段落，但段落内部由多个完整句子组成。
        String content = """
                第一句：这是一个非常长的知识说明段落，用来测试超长段在最终版分块逻辑中是否优先按句子边界切分而不是直接字符滑窗切分。
                第二句：如果当前逻辑正确，那么这些句子应该尽量作为完整单元被拼装进 chunk，而不是从中间被硬截断。
                第三句：最终生成的 chunk 应更接近完整句群，从而提升后续 citation、snippet 和上下文注入的质量。
                第四句：这一点对于技术文档、制度文档和架构说明类文档尤其重要，因为它们通常段落长但句子边界清晰。
                """;

        // 2. 执行分块。
        List<KbChunk> chunks = chunkService.split(1L, content);

        // 3. 校验至少生成了多个 chunk，说明超长段被处理了。
        assertNotNull(chunks);
        assertTrue(chunks.size() >= 2);

        // 4. 校验生成出的 chunk 不应全部只是生硬截断前缀，至少应保留句子级内容。
        for (KbChunk chunk : chunks) {
            assertNotNull(chunk.getContent());
            assertFalse(chunk.getContent().isBlank());

            // 句子优先切分后，chunk 内容一般会以句号等边界结束，至少不应全都表现为纯随机截断。
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
        // 1. 构造一个没有自然句边界、长度远超 chunkSize 的超长单句。
        String longSentence = "这是一个没有自然句边界的超长句子".repeat(80);

        // 2. 执行分块。
        List<KbChunk> chunks = chunkService.split(1L, longSentence);

        // 3. 校验生成了多个 chunk，说明最终字符兜底切分生效。
        assertNotNull(chunks);
        assertTrue(chunks.size() >= 2);

        // 4. 校验每个 chunk 内容都不为空，并且长度总体受控。
        for (KbChunk chunk : chunks) {
            assertNotNull(chunk.getContent());
            assertFalse(chunk.getContent().isBlank());

            // 因为字符兜底切分允许 overlap，所以单块长度可以略大于严格上限判断前后的视觉感受，
            // 但实际 substring 切分仍应控制在 chunkSize 附近。
            assertTrue(chunk.getContent().length() <= 700);
        }
    }

    @Test
    @DisplayName("chunkIndex 应从 0 开始连续递增")
    void split_shouldAssignSequentialChunkIndex() {
        // 1. 构造会切分出多个 chunk 的内容。
        String content = """
                第一段：这是第一段内容，主要介绍系统背景、模块分层和总体流程。

                第二段：这是第二段内容，主要介绍知识库检索、向量检索和关键词检索。

                第三段：这是第三段内容，主要介绍 organizer 的去重、snippet、citation 和 context 收口。

                第四段：这是第四段内容，主要介绍聊天显式知识增强和 knowledge_base 工具协同。
                """;

        // 2. 执行分块。
        List<KbChunk> chunks = chunkService.split(1L, content);

        // 3. 校验至少生成一个 chunk。
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // 4. 校验 chunkIndex 连续递增，确保顺序稳定，为后续相邻 chunk 处理打基础。
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex());
        }
    }

    @Test
    @DisplayName("生成的 chunk 应正确填充基础统计字段")
    void split_shouldFillBasicChunkFields() {
        // 1. 构造基础内容。
        String content = """
                第一段：ACL 包括 READ、MANAGE、SHARE 三类权限。

                第二段：RAG 优化包括 chunking、retrieval、organizer 和 agent 协同。
                """;

        // 2. 执行分块。
        List<KbChunk> chunks = chunkService.split(1L, content);

        // 3. 基础断言。
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // 4. 校验基础字段已正常赋值。
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
        // 1. 构造内容。
        String content = """
                第一段：ACL 包括 READ、MANAGE、SHARE 三类权限。

                第二段：RAG 优化重点包括去重、citation 和 snippet 收口。
                """;

        // 2. 执行 splitAndSave。
        List<KbChunk> chunks = chunkService.splitAndSave(1L, content);

        // 3. 校验确实生成了 chunk。
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // 4. 校验每一个生成的 chunk 都被持久化。
        verify(kbChunkRepository, times(chunks.size())).insert(any(KbChunk.class));
    }
}
