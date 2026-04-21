package com.jujiu.agent.service.kb.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekClient;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekResult;
import com.jujiu.agent.module.kb.application.service.impl.RagServiceImpl;
import com.jujiu.agent.module.kb.application.model.ChunkSearchResult;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.api.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.module.kb.api.response.CitationResponse;
import com.jujiu.agent.module.kb.api.response.KnowledgeQueryResponse;
import com.jujiu.agent.module.kb.domain.entity.KbQueryLog;
import com.jujiu.agent.module.kb.application.service.QueryLogService;
import com.jujiu.agent.module.kb.application.service.RetrievalResultOrganizer;
import com.jujiu.agent.module.kb.application.service.VectorSearchService;
import com.jujiu.agent.module.kb.application.model.OrganizedRetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RagServiceImpl} 单元测试。
 *
 * <p>本测试类用于锁定 RAG 服务在接入“检索结果整理层”之后的主流程行为，
 * 重点不是重复验证 organizer 内部算法细节，而是验证：
 * <ul>
 *     <li>RagService 是否调用 organizer</li>
 *     <li>空结果是否以 organizer 输出为准</li>
 *     <li>query / buildKnowledgeContext 是否统一复用 organizer</li>
 *     <li>最终 trace 是否记录 organizer 输出的 finalResults</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/17
 */
class RagServiceImplTest {

    private VectorSearchService vectorSearchService;
    private DeepSeekClient deepSeekClient;
    private KnowledgeBaseProperties knowledgeBaseProperties;
    private ObjectMapper objectMapper;
    private ExecutorService chatExecutor;
    private QueryLogService queryLogService;
    private RetrievalResultOrganizer retrievalResultOrganizer;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        // 1. 初始化依赖 mock。
        vectorSearchService = mock(VectorSearchService.class);
        deepSeekClient = mock(DeepSeekClient.class);
        knowledgeBaseProperties = new KnowledgeBaseProperties();
        objectMapper = new ObjectMapper();
        chatExecutor = mock(ExecutorService.class);
        queryLogService = mock(QueryLogService.class);
        retrievalResultOrganizer = mock(RetrievalResultOrganizer.class);

        // 2. 构造被测对象。
        ragService = new RagServiceImpl(
                vectorSearchService,
                deepSeekClient, 
                knowledgeBaseProperties,
                objectMapper,
                chatExecutor,
                retrievalResultOrganizer,
                queryLogService
                
        );
    }

    @Test
    @DisplayName("整理后无结果时应返回空引用且不调用模型")
    void query_shouldReturnEmptyResponse_whenOrganizedResultsEmpty() {
        // 1. 准备请求对象。
        QueryKnowledgeBaseRequest request = new QueryKnowledgeBaseRequest();
        request.setKbId(1L);
        request.setTopK(5);
        request.setQuestion("这份文档讲了什么");

        // 2. 模拟 search 有原始结果，但 organizer 整理后为空。
        List<ChunkSearchResult> rawResults = List.of(
                buildChunkResult(11L, 101L, "ACL 文档", "原始内容", 0.9D, 1)
        );

        when(vectorSearchService.search(1L, 1001L, "这份文档讲了什么", 5))
                .thenReturn(rawResults);

        when(retrievalResultOrganizer.organize(rawResults, "这份文档讲了什么"))
                .thenReturn(OrganizedRetrievalResult.builder()
                        .finalResults(List.of())
                        .citations(List.of())
                        .context("")
                        .rawResultCount(1)
                        .finalResultCount(0)
                        .emptyReason("ALL_FILTERED_AFTER_ORGANIZE")
                        .build());

        // 3. 执行 query。
        KnowledgeQueryResponse response = ragService.query(1001L, request);

        // 4. 校验返回统一空回答。
        assertNotNull(response);
        assertEquals("抱歉，知识库中没有足够信息支持回答该问题。", response.getAnswer());
        assertNotNull(response.getCitations());
        assertTrue(response.getCitations().isEmpty());
        assertEquals(0, response.getPromptTokens());
        assertEquals(0, response.getCompletionTokens());
        assertEquals(0, response.getTotalTokens());
        assertNotNull(response.getLatencyMs());

        // 5. 校验 query 过程中 organizer 被调用，但模型没有被调用。
        verify(vectorSearchService, times(1))
                .search(1L, 1001L, "这份文档讲了什么", 5);
        verify(retrievalResultOrganizer, times(1))
                .organize(rawResults, "这份文档讲了什么");
        verify(queryLogService, times(1))
                .saveQueryLog(eq(1001L), eq(1L), eq(request), eq(5), isNull(), eq(List.of()), anyLong(), eq("EMPTY"), isNull());
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    @DisplayName("整理后无上下文时 buildKnowledgeContext 应返回空字符串")
    void buildKnowledgeContext_shouldReturnBlank_whenOrganizedResultsEmpty() {
        // 1. 准备原始检索结果。
        List<ChunkSearchResult> rawResults = List.of(
                buildChunkResult(11L, 101L, "ACL 文档", "原始内容", 0.9D, 1)
        );

        when(vectorSearchService.search(1L, 1001L, "怎么接 ACL", 5))
                .thenReturn(rawResults);

        when(retrievalResultOrganizer.organize(rawResults, "怎么接 ACL"))
                .thenReturn(OrganizedRetrievalResult.builder()
                        .finalResults(List.of())
                        .citations(List.of())
                        .context("")
                        .rawResultCount(1)
                        .finalResultCount(0)
                        .emptyReason("ALL_FILTERED_AFTER_ORGANIZE")
                        .build());

        // 2. 执行上下文构造。
        String context = ragService.buildKnowledgeContext(1001L, 1L, "怎么接 ACL", 5);

        // 3. 校验 organizer 决定了返回空字符串。
        assertNotNull(context);
        assertTrue(context.isBlank());

        verify(vectorSearchService, times(1))
                .search(1L, 1001L, "怎么接 ACL", 5);
        verify(retrievalResultOrganizer, times(1))
                .organize(rawResults, "怎么接 ACL");
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    @DisplayName("有整理结果时应调用模型并返回 organizer 生成的引用")
    void query_shouldCallModelAndReturnCitations_whenOrganizedResultsPresent() {
        // 1. 准备请求对象。
        QueryKnowledgeBaseRequest request = new QueryKnowledgeBaseRequest();
        request.setKbId(1L);
        request.setTopK(5);
        request.setQuestion("ACL 怎么做");

        // 2. 准备原始检索结果。
        List<ChunkSearchResult> rawResults = List.of(
                buildChunkResult(11L, 101L, "ACL 设计文档", "ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限", 0.95D, 1)
        );

        // 3. 准备 organizer 整理后的结果。
        List<ChunkSearchResult> finalResults = List.of(
                buildChunkResult(11L, 101L, "ACL 设计文档", "ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限", 0.95D, 1)
        );

        List<CitationResponse> citations = List.of(
                CitationResponse.builder()
                        .chunkId(11L)
                        .documentId(101L)
                        .documentTitle("ACL 设计文档")
                        .snippet("ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限")
                        .score(0.95D)
                        .rank(1)
                        .build()
        );

        when(vectorSearchService.search(1L, 1001L, "ACL 怎么做", 5))
                .thenReturn(rawResults);

        when(retrievalResultOrganizer.organize(rawResults, "ACL 怎么做"))
                .thenReturn(OrganizedRetrievalResult.builder()
                        .finalResults(finalResults)
                        .citations(citations)
                        .context("[1] ACL 设计文档\nACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限\n\n")
                        .rawResultCount(1)
                        .finalResultCount(1)
                        .emptyReason("NONE")
                        .build());

        // 4. 准备模型返回。
        DeepSeekResult deepSeekResult = new DeepSeekResult();
        deepSeekResult.setReply("ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。");
        deepSeekResult.setPromptTokens(100);
        deepSeekResult.setCompletionTokens(50);
        deepSeekResult.setTotalTokens(150);

        KbQueryLog queryLog = new KbQueryLog();
        queryLog.setId(999L);

        when(deepSeekClient.chat(anyList())).thenReturn(deepSeekResult);
        when(queryLogService.saveQueryLog(
                eq(1001L),
                eq(1L),
                eq(request),
                eq(5),
                eq(deepSeekResult),
                eq(citations),
                anyLong(),
                eq("SUCCESS"),
                isNull()
        )).thenReturn(queryLog);

        // 5. 执行 query。
        KnowledgeQueryResponse response = ragService.query(1001L, request);

        // 6. 校验响应来自 organizer 产出的 citations。
        assertNotNull(response);
        assertEquals("ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限。", response.getAnswer());
        assertEquals(1, response.getCitations().size());

        CitationResponse citation = response.getCitations().get(0);
        assertEquals(11L, citation.getChunkId());
        assertEquals(101L, citation.getDocumentId());
        assertEquals("ACL 设计文档", citation.getDocumentTitle());
        assertEquals("ACL 包括 READ、rebuildFailedIndexes 是做什么的、SHARE 三类权限", citation.getSnippet());

        assertEquals(100, response.getPromptTokens());
        assertEquals(50, response.getCompletionTokens());
        assertEquals(150, response.getTotalTokens());

        // 7. 校验最终 retrieval trace 记录的是 organizer 的 finalResults，而不是 rawResults。
        verify(vectorSearchService, times(1))
                .search(1L, 1001L, "ACL 怎么做", 5);
        verify(retrievalResultOrganizer, times(1))
                .organize(rawResults, "ACL 怎么做");
        verify(deepSeekClient, times(1)).chat(anyList());
        verify(queryLogService, times(1)).saveRetrievalTrace(999L, finalResults);
    }

    /**
     * 构造测试用检索结果。
     *
     * @param chunkId       chunk ID
     * @param documentId    文档 ID
     * @param documentTitle 文档标题
     * @param content       内容
     * @param score         分数
     * @param rank          排名
     * @return 检索结果
     */
    private ChunkSearchResult buildChunkResult(Long chunkId,
                                               Long documentId,
                                               String documentTitle,
                                               String content,
                                               Double score,
                                               Integer rank) {
        return ChunkSearchResult.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .documentTitle(documentTitle)
                .content(content)
                .score(score)
                .rank(rank)
                .build();
    }
}
