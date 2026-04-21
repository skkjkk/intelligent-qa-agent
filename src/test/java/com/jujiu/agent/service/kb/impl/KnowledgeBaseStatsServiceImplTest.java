package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.module.kb.application.service.impl.KnowledgeBaseStatsServiceImpl;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.api.response.KbStatsOverviewResponse;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryFeedbackMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeBaseStatsServiceImplTest {

    private KbDocumentMapper kbDocumentMapper;
    private KbQueryLogMapper kbQueryLogMapper;
    private KbQueryFeedbackMapper kbQueryFeedbackMapper;
    private KnowledgeBaseStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        kbDocumentMapper = mock(KbDocumentMapper.class);
        kbQueryLogMapper = mock(KbQueryLogMapper.class);
        kbQueryFeedbackMapper = mock(KbQueryFeedbackMapper.class);
        service = new KnowledgeBaseStatsServiceImpl(
                kbDocumentMapper, kbQueryLogMapper, kbQueryFeedbackMapper
        );
    }

    @Test
    @DisplayName("getOverview 应正确汇总概览+趋势+反馈质量")
    void shouldReturnOverviewWithTrendAndFeedbackQuality() {
        Long userId = 1001L;
        Long kbId = 1L;

        when(kbDocumentMapper.selectCount(any())).thenReturn(
                10L, // totalDocuments
                8L,  // successDocuments
                1L,  // processingDocuments
                1L   // failedDocuments
        );

        when(kbQueryLogMapper.aggregateSummary(userId, kbId)).thenReturn(Map.of(
                "totalQueries", 50L,
                "successQueries", 45L
        ));

        when(kbQueryFeedbackMapper.aggregateQuality(userId, kbId)).thenReturn(Map.of(
                "totalFeedbacks", 20L,
                "helpfulCount", 15L,
                "unhelpfulCount", 5L,
                "avgRating", 4.2
        ));

        when(kbDocumentMapper.countCreatedSince(eq(userId), eq(kbId), any())).thenReturn(3L, 12L);
        when(kbQueryLogMapper.countSince(eq(userId), eq(kbId), any())).thenReturn(9L, 35L);

        when(kbQueryLogMapper.aggregateTrend(eq(userId), eq(kbId), any())).thenReturn(List.of(
                Map.of("dayVal", "2026-04-19", "dayCount", 2L),
                Map.of("dayVal", "2026-04-20", "dayCount", 3L)
        ));
        when(kbDocumentMapper.aggregateCreatedTrend(eq(userId), eq(kbId), any())).thenReturn(List.of(
                Map.of("dayVal", "2026-04-19", "dayCount", 1L),
                Map.of("dayVal", "2026-04-20", "dayCount", 2L)
        ));

        KbStatsOverviewResponse result = service.getOverview(
                userId, kbId, 30, ZoneId.of("Asia/Shanghai"), 10
        );

        assertNotNull(result);
        assertEquals(10L, result.getTotalDocuments());
        assertEquals(8L, result.getSuccessDocuments());
        assertEquals(1L, result.getProcessingDocuments());
        assertEquals(1L, result.getFailedDocuments());

        assertEquals(50L, result.getTotalQueries());
        assertEquals(45L, result.getSuccessQueries());

        assertEquals(20L, result.getTotalFeedbacks());
        assertEquals(15L, result.getHelpfulCount());
        assertEquals(5L, result.getUnhelpfulCount());
        assertEquals(4.2D, result.getAvgRating());

        assertEquals(3L, result.getDocumentsLast7Days());
        assertEquals(12L, result.getDocumentsLast30Days());
        assertEquals(9L, result.getQueriesLast7Days());
        assertEquals(35L, result.getQueriesLast30Days());

        assertEquals(2, result.getQueryTrend30Days().size());
        assertEquals("2026-04-19", result.getQueryTrend30Days().get(0).getDay());
        assertEquals(2L, result.getQueryTrend30Days().get(0).getCount());
        assertEquals(2, result.getDocumentTrend30Days().size());
    }

    @Test
    @DisplayName("getOverview 当 userId 非法时应抛异常")
    void shouldThrowWhenUserIdInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getOverview(null, 1L, 30, ZoneId.of("Asia/Shanghai"), 10));

        assertTrue(ex.getMessage().contains("userId 不能为空"));
        verifyNoInteractions(kbDocumentMapper, kbQueryLogMapper, kbQueryFeedbackMapper);
    }
}
