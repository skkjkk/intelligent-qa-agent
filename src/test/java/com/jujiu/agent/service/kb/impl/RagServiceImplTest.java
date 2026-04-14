package com.jujiu.agent.service.kb.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.repository.KbRetrievalTraceRepository;
import com.jujiu.agent.service.kb.QueryLogService;
import com.jujiu.agent.service.kb.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagServiceImplTest {

    private VectorSearchService vectorSearchService;
    private DeepSeekClient deepSeekClient;
    private KbQueryLogRepository kbQueryLogRepository;
    private KbRetrievalTraceRepository kbRetrievalTraceRepository;
    private KnowledgeBaseProperties knowledgeBaseProperties;
    private ObjectMapper objectMapper;
    private ExecutorService chatExecutor;
    private QueryLogService queryLogService;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        vectorSearchService = mock(VectorSearchService.class);
        deepSeekClient = mock(DeepSeekClient.class);
        kbQueryLogRepository = mock(KbQueryLogRepository.class);
        kbRetrievalTraceRepository = mock(KbRetrievalTraceRepository.class);
        knowledgeBaseProperties = new KnowledgeBaseProperties();
        objectMapper = new ObjectMapper();
        chatExecutor = mock(ExecutorService.class);
        queryLogService = mock(QueryLogService.class);

        ragService = new RagServiceImpl(
                vectorSearchService,
                deepSeekClient,
                kbQueryLogRepository,
                kbRetrievalTraceRepository,
                knowledgeBaseProperties,
                objectMapper,
                chatExecutor,
                queryLogService
        );
    }

    @Test
    @DisplayName("ACL 过滤后无结果时应返回空引用且不调用模型")
    void query_shouldReturnEmptyResponse_whenSearchResultsEmpty() {
        QueryKnowledgeBaseRequest request = new QueryKnowledgeBaseRequest();
        request.setKbId(1L);
        request.setTopK(5);
        request.setQuestion("这份文档讲了什么");

        when(vectorSearchService.search(1L, 1001L, "这份文档讲了什么", 5))
                .thenReturn(List.of());

        KnowledgeQueryResponse response = ragService.query(1001L, request);

        assertNotNull(response);
        assertEquals("抱歉，知识库中没有足够信息支持回答该问题。", response.getAnswer());
        assertNotNull(response.getCitations());
        assertTrue(response.getCitations().isEmpty());
        assertEquals(0, response.getPromptTokens());
        assertEquals(0, response.getCompletionTokens());
        assertEquals(0, response.getTotalTokens());
        assertNotNull(response.getLatencyMs());

        verify(vectorSearchService, times(1))
                .search(1L, 1001L, "这份文档讲了什么", 5);
        verify(queryLogService, times(1))
                .saveQueryLog(eq(1001L), eq(1L), eq(request), eq(5), isNull(), eq(List.of()), anyLong(), eq("EMPTY"), isNull());
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    @DisplayName("ACL 过滤后无上下文时 buildKnowledgeContext 应返回空字符串")
    void buildKnowledgeContext_shouldReturnBlank_whenSearchResultsEmpty() {
        when(vectorSearchService.search(1L, 1001L, "怎么接 ACL", 5))
                .thenReturn(List.of());

        String context = ragService.buildKnowledgeContext(1001L, 1L, "怎么接 ACL", 5);

        assertNotNull(context);
        assertTrue(context.isBlank());

        verify(vectorSearchService, times(1))
                .search(1L, 1001L, "怎么接 ACL", 5);
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    @DisplayName("有检索结果时应调用模型并返回引用")
    void query_shouldCallModelAndReturnCitations_whenSearchResultsPresent() {
        QueryKnowledgeBaseRequest request = new QueryKnowledgeBaseRequest();
        request.setKbId(1L);
        request.setTopK(5);
        request.setQuestion("ACL 怎么做");

        when(vectorSearchService.search(1L, 1001L, "ACL 怎么做", 5))
                .thenReturn(List.of(
                        com.jujiu.agent.common.result.ChunkSearchResult.builder()
                                .chunkId(11L)
                                .documentId(101L)
                                .documentTitle("ACL 设计文档")
                                .content("ACL 包括 READ、MANAGE、SHARE 三类权限")
                                .score(0.95D)
                                .rank(1)
                                .build()
                ));

        DeepSeekResult deepSeekResult = new DeepSeekResult();
        deepSeekResult.setReply("ACL 包括 READ、MANAGE、SHARE 三类权限。");
        deepSeekResult.setPromptTokens(100);
        deepSeekResult.setCompletionTokens(50);
        deepSeekResult.setTotalTokens(150);

        com.jujiu.agent.model.entity.KbQueryLog queryLog = new com.jujiu.agent.model.entity.KbQueryLog();
        queryLog.setId(999L);

        when(deepSeekClient.chat(anyList())).thenReturn(deepSeekResult);
        when(queryLogService.saveQueryLog(
                eq(1001L),
                eq(1L),
                eq(request),
                eq(5),
                eq(deepSeekResult),
                anyList(),
                anyLong(),
                eq("SUCCESS"),
                isNull()
        )).thenReturn(queryLog);

        KnowledgeQueryResponse response = ragService.query(1001L, request);

        assertNotNull(response);
        assertEquals("ACL 包括 READ、MANAGE、SHARE 三类权限。", response.getAnswer());
        assertEquals(1, response.getCitations().size());

        CitationResponse citation = response.getCitations().get(0);
        assertEquals(11L, citation.getChunkId());
        assertEquals(101L, citation.getDocumentId());
        assertEquals("ACL 设计文档", citation.getDocumentTitle());

        assertEquals(100, response.getPromptTokens());
        assertEquals(50, response.getCompletionTokens());
        assertEquals(150, response.getTotalTokens());

        verify(vectorSearchService, times(1))
                .search(1L, 1001L, "ACL 怎么做", 5);
        verify(deepSeekClient, times(1)).chat(anyList());
        verify(queryLogService, times(1)).saveRetrievalTrace(eq(999L), anyList());
    }

}
