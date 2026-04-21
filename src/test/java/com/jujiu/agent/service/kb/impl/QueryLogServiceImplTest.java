package com.jujiu.agent.service.kb.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.kb.application.service.impl.QueryLogServiceImpl;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.api.request.QueryFeedbackRequest;
import com.jujiu.agent.module.kb.api.response.KbQueryHistoryResponse;
import com.jujiu.agent.module.kb.domain.entity.KbQueryFeedback;
import com.jujiu.agent.module.kb.domain.entity.KbQueryLog;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryFeedbackMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbQueryLogMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbRetrievalTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 知识库查询日志服务单元测试。
 *
 * <p>用于验证查询历史查询、反馈保存以及用户权限校验等核心逻辑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */

public class QueryLogServiceImplTest {
    private KbQueryLogMapper kbQueryLogMapper;
    private KbRetrievalTraceMapper kbRetrievalTraceMapper;
    private KbQueryFeedbackMapper kbQueryFeedbackMapper;
    private QueryLogServiceImpl queryLogService;

    @BeforeEach
    void setUp() {
        kbQueryLogMapper = mock(KbQueryLogMapper.class);
        kbRetrievalTraceMapper = mock(KbRetrievalTraceMapper.class);
        kbQueryFeedbackMapper = mock(KbQueryFeedbackMapper.class);

        queryLogService = new QueryLogServiceImpl(
                kbQueryLogMapper,
                kbRetrievalTraceMapper,
                new ObjectMapper(),
                kbQueryFeedbackMapper
                
        );
    }

    @Test
    @DisplayName("saveFeedback 应正常保存反馈")
    void shouldSaveFeedbackSuccessfully() {
        Long userId = 1001L;
        Long queryLogId = 10L;

        QueryFeedbackRequest request = new QueryFeedbackRequest();
        request.setHelpful(true);
        request.setRating(5);
        request.setFeedbackContent("回答很准确");

        KbQueryLog queryLog = KbQueryLog.builder()
                .id(queryLogId)
                .userId(userId)
                .kbId(1L)
                .question("问题")
                .answer("答案")
                .createdAt(LocalDateTime.now())
                .build();

        when(kbQueryLogMapper.selectById(queryLogId)).thenReturn(queryLog);

        queryLogService.saveFeedback(userId, queryLogId, request);

        ArgumentCaptor<KbQueryFeedback> feedbackCaptor = ArgumentCaptor.forClass(KbQueryFeedback.class);
        verify(kbQueryFeedbackMapper, times(1)).insert(feedbackCaptor.capture());

        KbQueryFeedback savedFeedback = feedbackCaptor.getValue();
        assertEquals(queryLogId, savedFeedback.getQueryLogId());
        assertEquals(userId, savedFeedback.getUserId());
        assertEquals(1, savedFeedback.getHelpful());
        assertEquals(5, savedFeedback.getRating());
        assertEquals("回答很准确", savedFeedback.getFeedbackContent());
        assertNotNull(savedFeedback.getCreatedAt());
    }

    @Test
    @DisplayName("saveFeedback 对不存在的 queryLog 应抛异常")
    void shouldThrowWhenQueryLogNotFound() {
        Long userId = 1001L;
        Long queryLogId = 10L;

        QueryFeedbackRequest request = new QueryFeedbackRequest();
        request.setHelpful(true);

        when(kbQueryLogMapper.selectById(queryLogId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> queryLogService.saveFeedback(userId, queryLogId, request));

        assertEquals("查询记录不存在", exception.getMessage());
        verify(kbQueryFeedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("saveFeedback 对非本人 queryLog 应抛异常")
    void shouldThrowWhenQueryLogBelongsToAnotherUser() {
        Long userId = 1001L;
        Long queryLogId = 10L;

        QueryFeedbackRequest request = new QueryFeedbackRequest();
        request.setHelpful(false);

        KbQueryLog queryLog = KbQueryLog.builder()
                .id(queryLogId)
                .userId(2002L)
                .kbId(1L)
                .question("问题")
                .answer("答案")
                .createdAt(LocalDateTime.now())
                .build();

        when(kbQueryLogMapper.selectById(queryLogId)).thenReturn(queryLog);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> queryLogService.saveFeedback(userId, queryLogId, request));

        assertEquals("无权反馈该查询记录", exception.getMessage());
        verify(kbQueryFeedbackMapper, never()).insert(any());
    }

    @Test
    @DisplayName("listQueryHistory 应正确映射历史记录")
    void shouldMapQueryHistoryCorrectly() {
        Long userId = 1001L;
        Long kbId = 1L;
        LocalDateTime now = LocalDateTime.now();

        KbQueryLog log1 = KbQueryLog.builder()
                .id(1L)
                .kbId(kbId)
                .userId(userId)
                .question("问题1")
                .answer("答案1")
                .querySource("KB_API")
                .retrievalMode("HYBRID")
                .retrievalTopK(5)
                .status("SUCCESS")
                .latencyMs(1200)
                .totalTokens(300)
                .createdAt(now)
                .build();

        when(kbQueryLogMapper.selectList(any())).thenReturn(List.of(log1));

        List<KbQueryHistoryResponse> result = queryLogService.listQueryHistory(userId, kbId);

        assertNotNull(result);
        assertEquals(1, result.size());

        KbQueryHistoryResponse history = result.get(0);
        assertEquals(1L, history.getQueryLogId());
        assertEquals(kbId, history.getKbId());
        assertEquals("问题1", history.getQuestion());
        assertEquals("答案1", history.getAnswer());
        assertEquals("KB_API", history.getQuerySource());
        assertEquals("HYBRID", history.getRetrievalMode());
        assertEquals(5, history.getRetrievalTopK());
        assertEquals("SUCCESS", history.getStatus());
        assertEquals(1200, history.getLatencyMs());
        assertEquals(300, history.getTotalTokens());
        assertEquals(now, history.getCreatedAt());
    }
}
