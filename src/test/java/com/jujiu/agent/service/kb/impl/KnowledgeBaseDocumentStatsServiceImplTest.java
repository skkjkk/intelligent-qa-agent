package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.module.kb.application.service.impl.KnowledgeBaseDocumentStatsServiceImpl;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.api.response.KbDocumentStatsResponse;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeBaseDocumentStatsServiceImplTest {

    private KbDocumentMapper kbDocumentMapper;
    private KnowledgeBaseDocumentStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        kbDocumentMapper = mock(KbDocumentMapper.class);
        service = new KnowledgeBaseDocumentStatsServiceImpl(kbDocumentMapper);
    }

    @Test
    @DisplayName("getDocumentStats 应正确返回文档统计+分布+趋势")
    void shouldReturnDocumentStatsWithDistributionAndTrend() {
        Long userId = 1001L;
        Long kbId = 1L;

        when(kbDocumentMapper.selectCount(any())).thenReturn(
                6L, // total
                4L, // success
                1L, // processing
                1L, // failed
                2L, // pdf
                1L, // docx
                1L, // md
                1L, // txt
                1L  // html
        );

        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(
                KbDocument.builder().chunkCount(10).build(),
                KbDocument.builder().chunkCount(20).build(),
                KbDocument.builder().chunkCount(30).build()
        ));

        when(kbDocumentMapper.aggregateByFileType(userId, kbId)).thenReturn(List.of(
                Map.of("dimName", "pdf", "dimCount", 2L),
                Map.of("dimName", "docx", "dimCount", 1L)
        ));

        when(kbDocumentMapper.aggregateByStatus(userId, kbId)).thenReturn(List.of(
                Map.of("dimName", "SUCCESS", "dimCount", 4L),
                Map.of("dimName", "FAILED", "dimCount", 1L)
        ));

        when(kbDocumentMapper.aggregateCreatedTrend(eq(userId), eq(kbId), any())).thenReturn(List.of(
                Map.of("dayVal", "2026-04-19", "dayCount", 1L),
                Map.of("dayVal", "2026-04-20", "dayCount", 2L)
        ));

        KbDocumentStatsResponse result = service.getDocumentStats(
                userId, kbId, 30, ZoneId.of("Asia/Shanghai"), 10
        );

        assertNotNull(result);
        assertEquals(6L, result.getTotalDocuments());
        assertEquals(4L, result.getSuccessDocuments());
        assertEquals(1L, result.getProcessingDocuments());
        assertEquals(1L, result.getFailedDocuments());

        assertEquals(2L, result.getPdfDocuments());
        assertEquals(1L, result.getDocxDocuments());
        assertEquals(1L, result.getMdDocuments());
        assertEquals(1L, result.getTxtDocuments());
        assertEquals(1L, result.getHtmlDocuments());

        assertEquals(60L, result.getTotalChunks());

        assertEquals(2, result.getFileTypeDistribution().size());
        assertEquals("pdf", result.getFileTypeDistribution().get(0).getName());
        assertEquals(2L, result.getFileTypeDistribution().get(0).getCount());

        assertEquals(2, result.getStatusDistribution().size());

        assertEquals(2, result.getTrend7Days().size());
        assertEquals(2, result.getTrend30Days().size());

        verify(kbDocumentMapper, times(9)).selectCount(any());
        verify(kbDocumentMapper, times(1)).selectList(any());
        verify(kbDocumentMapper, times(1)).aggregateByFileType(userId, kbId);
        verify(kbDocumentMapper, times(1)).aggregateByStatus(userId, kbId);
        verify(kbDocumentMapper, times(2)).aggregateCreatedTrend(eq(userId), eq(kbId), any());
    }

    @Test
    @DisplayName("getDocumentStats 当 userId 非法时应抛异常")
    void shouldThrowWhenUserIdInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDocumentStats(0L, 1L, 30, ZoneId.of("Asia/Shanghai"), 10));

        assertTrue(ex.getMessage().contains("userId 不能为空"));
        verifyNoInteractions(kbDocumentMapper);
    }
}
