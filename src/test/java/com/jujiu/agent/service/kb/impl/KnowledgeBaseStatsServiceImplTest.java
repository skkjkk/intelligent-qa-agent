package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.model.dto.response.KbStatsOverviewResponse;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.repository.KbQueryFeedbackRepository;
import com.jujiu.agent.repository.KbQueryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 知识库统计服务单元测试。
 *
 * <p>用于验证知识库概览统计查询与参数校验等核心逻辑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
class KnowledgeBaseStatsServiceImplTest {

    private KbDocumentRepository kbDocumentRepository;
    private KbQueryLogRepository kbQueryLogRepository;
    private KbQueryFeedbackRepository kbQueryFeedbackRepository;
    private KnowledgeBaseStatsServiceImpl knowledgeBaseStatsService;

    @BeforeEach
    void setUp() {
        kbDocumentRepository = mock(KbDocumentRepository.class);
        kbQueryLogRepository = mock(KbQueryLogRepository.class);
        kbQueryFeedbackRepository = mock(KbQueryFeedbackRepository.class);

        knowledgeBaseStatsService = new KnowledgeBaseStatsServiceImpl(
                kbDocumentRepository,
                kbQueryLogRepository,
                kbQueryFeedbackRepository
        );
    }

    @Test
    @DisplayName("getOverview 应正确汇总统计结果")
    void shouldReturnCorrectOverviewStats() {
        Long userId = 1001L;
        Long kbId = 1L;

        when(kbDocumentRepository.selectCount(any())).thenReturn(
                3L, // totalDocuments
                2L, // successDocuments
                1L, // processingDocuments
                0L  // failedDocuments
        );

        when(kbQueryLogRepository.selectCount(any())).thenReturn(
                10L, // totalQueries
                9L   // successQueries
        );

        when(kbQueryFeedbackRepository.selectCount(any())).thenReturn(4L);

        KbStatsOverviewResponse result = knowledgeBaseStatsService.getOverview(userId, kbId);

        assertNotNull(result);
        assertEquals(3L, result.getTotalDocuments());
        assertEquals(2L, result.getSuccessDocuments());
        assertEquals(1L, result.getProcessingDocuments());
        assertEquals(0L, result.getFailedDocuments());
        assertEquals(10L, result.getTotalQueries());
        assertEquals(9L, result.getSuccessQueries());
        assertEquals(4L, result.getTotalFeedbacks());

        verify(kbDocumentRepository, times(4)).selectCount(any());
        verify(kbQueryLogRepository, times(2)).selectCount(any());
        verify(kbQueryFeedbackRepository, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("getOverview 当 userId 非法时应抛异常")
    void shouldThrowWhenUserIdInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseStatsService.getOverview(null, 1L));

        assertTrue(exception.getMessage().contains("userId 不能为空"));

        verifyNoInteractions(kbDocumentRepository);
        verifyNoInteractions(kbQueryLogRepository);
        verifyNoInteractions(kbQueryFeedbackRepository);
    }
}
