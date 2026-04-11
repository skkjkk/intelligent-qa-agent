package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.model.dto.response.KbDocumentStatsResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 知识库文档统计服务单元测试。
 *
 * <p>用于验证文档状态统计、文件类型统计与分块数量汇总逻辑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
class KnowledgeBaseDocumentStatsServiceImplTest {

    private KbDocumentRepository kbDocumentRepository;
    private KnowledgeBaseDocumentStatsServiceImpl documentStatsService;

    @BeforeEach
    void setUp() {
        kbDocumentRepository = mock(KbDocumentRepository.class);
        documentStatsService = new KnowledgeBaseDocumentStatsServiceImpl(kbDocumentRepository);
    }

    @Test
    @DisplayName("getDocumentStats 应正确汇总文档统计结果")
    void shouldReturnCorrectDocumentStats() {
        Long userId = 1001L;
        Long kbId = 1L;

        when(kbDocumentRepository.selectCount(any())).thenReturn(
                3L, // totalDocuments
                2L, // successDocuments
                1L, // processingDocuments
                0L, // failedDocuments
                1L, // pdfDocuments
                0L, // docxDocuments
                1L, // mdDocuments
                1L, // txtDocuments
                0L  // htmlDocuments
        );

        List<KbDocument> documents = List.of(
                KbDocument.builder().chunkCount(10).build(),
                KbDocument.builder().chunkCount(20).build(),
                KbDocument.builder().chunkCount(30).build()
        );
        when(kbDocumentRepository.selectList(any())).thenReturn(documents);

        KbDocumentStatsResponse result = documentStatsService.getDocumentStats(userId, kbId);

        assertNotNull(result);
        assertEquals(3L, result.getTotalDocuments());
        assertEquals(2L, result.getSuccessDocuments());
        assertEquals(1L, result.getProcessingDocuments());
        assertEquals(0L, result.getFailedDocuments());
        assertEquals(1L, result.getPdfDocuments());
        assertEquals(0L, result.getDocxDocuments());
        assertEquals(1L, result.getMdDocuments());
        assertEquals(1L, result.getTxtDocuments());
        assertEquals(0L, result.getHtmlDocuments());
        assertEquals(60L, result.getTotalChunks());

        verify(kbDocumentRepository, times(9)).selectCount(any());
        verify(kbDocumentRepository, times(1)).selectList(any());
    }

    @Test
    @DisplayName("getDocumentStats 当 userId 非法时应抛异常")
    void shouldThrowWhenUserIdInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> documentStatsService.getDocumentStats(0L, 1L));

        assertTrue(exception.getMessage().contains("userId 不能为空"));

        verifyNoInteractions(kbDocumentRepository);
    }
}
