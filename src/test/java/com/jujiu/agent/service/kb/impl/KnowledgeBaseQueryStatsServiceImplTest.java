package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.module.kb.application.service.impl.KnowledgeBaseQueryStatsServiceImpl;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.api.response.KbQueryStatsResponse;
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

class KnowledgeBaseQueryStatsServiceImplTest {

    private KbQueryLogMapper kbQueryLogMapper;
    private KbQueryFeedbackMapper kbQueryFeedbackMapper;
    private KnowledgeBaseQueryStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        kbQueryLogMapper = mock(KbQueryLogMapper.class);
        kbQueryFeedbackMapper = mock(KbQueryFeedbackMapper.class);
        service = new KnowledgeBaseQueryStatsServiceImpl(kbQueryLogMapper, kbQueryFeedbackMapper);
    }

    @Test
    @DisplayName("getQueryStats 应正确返回查询统计+质量+趋势")
    void shouldReturnQueryStatsWithQualityAndTrend() {
        Long userId = 1001L;
        Long kbId = 1L;

        when(kbQueryLogMapper.aggregateSummary(userId, kbId)).thenReturn(Map.of(
                "totalQueries", 10L,
                "successQueries", 8L,
                "emptyQueries", 1L,
                "failedQueries", 1L,
                "avgLatencyMs", 1200L,
                "avgTotalTokens", 320L
        ));

        when(kbQueryFeedbackMapper.aggregateQuality(userId, kbId)).thenReturn(Map.of(
                "helpfulCount", 6L,
                "unhelpfulCount", 2L,
                "avgRating", 4.5
        ));

        when(kbQueryFeedbackMapper.aggregateRatingDistribution(userId, kbId)).thenReturn(List.of(
                Map.of("dimName", "4", "dimCount", 3L),
                Map.of("dimName", "5", "dimCount", 5L)
        ));

        when(kbQueryLogMapper.aggregateTrend(eq(userId), eq(kbId), any())).thenReturn(List.of(
                Map.of("dayVal", "2026-04-19", "dayCount", 4L),
                Map.of("dayVal", "2026-04-20", "dayCount", 6L)
        ));

        when(kbQueryLogMapper.selectCount(any())).thenReturn(10L, 8L, 1L, 1L);

        KbQueryStatsResponse result = service.getQueryStats(
                userId, kbId, 30, ZoneId.of("Asia/Shanghai"), 10
        );

        assertNotNull(result);
        assertEquals(10L, result.getTotalQueries());
        assertEquals(8L, result.getSuccessQueries());
        assertEquals(1L, result.getEmptyQueries());
        assertEquals(1L, result.getFailedQueries());
        assertEquals(1200L, result.getAvgLatencyMs());
        assertEquals(320L, result.getAvgTotalTokens());

        assertEquals(6L, result.getHelpfulCount());
        assertEquals(2L, result.getUnhelpfulCount());
        assertEquals(4.5D, result.getAvgRating());

        assertEquals(2, result.getRatingDistribution().size());
        assertEquals("4", result.getRatingDistribution().get(0).getName());
        assertEquals(3L, result.getRatingDistribution().get(0).getCount());

        assertEquals(2, result.getTrend7Days().size());
        assertEquals(2, result.getTrend30Days().size());

        verify(kbQueryLogMapper, times(2)).aggregateTrend(eq(userId), eq(kbId), any());
        verify(kbQueryLogMapper, times(4)).selectCount(any());
    }

    @Test
    @DisplayName("getQueryStats 当 userId 非法时应抛异常")
    void shouldThrowWhenUserIdInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getQueryStats(-1L, 1L, 30, ZoneId.of("Asia/Shanghai"), 10));

        assertTrue(ex.getMessage().contains("userId 不能为空"));
        verifyNoInteractions(kbQueryLogMapper, kbQueryFeedbackMapper);
    }
}
