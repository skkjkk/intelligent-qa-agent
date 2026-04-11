package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.model.dto.response.KbQueryStatsResponse;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.repository.KbQueryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 知识库问答统计服务单元测试。
 *
 * <p>用于验证问答状态统计、平均耗时与平均 Token 汇总逻辑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
class KnowledgeBaseQueryStatsServiceImplTest {

    private KbQueryLogRepository kbQueryLogRepository;
    private KnowledgeBaseQueryStatsServiceImpl queryStatsService;

    @BeforeEach
    void setUp() {
        kbQueryLogRepository = mock(KbQueryLogRepository.class);
        queryStatsService = new KnowledgeBaseQueryStatsServiceImpl(kbQueryLogRepository);
    }

    @Test
    @DisplayName("getQueryStats 应正确汇总问答统计结果")
    void shouldReturnCorrectQueryStats() {
        Long userId = 1001L;
        Long kbId = 1L;

        when(kbQueryLogRepository.selectCount(any())).thenReturn(
                10L, // totalQueries
                8L,  // successQueries
                1L,  // emptyQueries
                1L   // failedQueries
        );

        List<KbQueryLog> logs = List.of(
                KbQueryLog.builder().latencyMs(1000).totalTokens(100).build(),
                KbQueryLog.builder().latencyMs(2000).totalTokens(200).build(),
                KbQueryLog.builder().latencyMs(3000).totalTokens(300).build()
        );
        when(kbQueryLogRepository.selectList(any())).thenReturn(logs);

        KbQueryStatsResponse result = queryStatsService.getQueryStats(userId, kbId);

        assertNotNull(result);
        assertEquals(10L, result.getTotalQueries());
        assertEquals(8L, result.getSuccessQueries());
        assertEquals(1L, result.getEmptyQueries());
        assertEquals(1L, result.getFailedQueries());
        assertEquals(2000L, result.getAvgLatencyMs());
        assertEquals(200L, result.getAvgTotalTokens());

        verify(kbQueryLogRepository, times(4)).selectCount(any());
        verify(kbQueryLogRepository, times(1)).selectList(any());
    }

    @Test
    @DisplayName("getQueryStats 当 userId 非法时应抛异常")
    void shouldThrowWhenUserIdInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> queryStatsService.getQueryStats(-1L, 1L));

        assertTrue(exception.getMessage().contains("userId 不能为空"));

        verifyNoInteractions(kbQueryLogRepository);
    }
}
