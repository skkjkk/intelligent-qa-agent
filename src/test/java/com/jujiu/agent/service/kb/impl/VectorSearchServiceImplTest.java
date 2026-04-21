package com.jujiu.agent.service.kb.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.util.ObjectBuilder;
import com.jujiu.agent.module.kb.application.service.impl.VectorSearchServiceImpl;
import com.jujiu.agent.module.kb.application.model.ChunkSearchResult;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.infrastructure.search.KbChunkIndexDocument;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.application.service.DocumentAclService;
import com.jujiu.agent.module.kb.application.service.EmbeddingService;
import com.jujiu.agent.module.kb.application.service.RetrievalRerankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VectorSearchServiceImplTest {

    private EmbeddingService embeddingService;
    private KbDocumentMapper kbDocumentMapper;
    private ElasticsearchClient elasticsearchClient;
    private KnowledgeBaseProperties knowledgeBaseProperties;
    private DocumentAclService documentAclService;
    private VectorSearchServiceImpl vectorSearchService;
    private RetrievalRerankService retrievalRerankService;

    @BeforeEach
    void setUp() {
        embeddingService = mock(EmbeddingService.class);
        kbDocumentMapper = mock(KbDocumentMapper.class);
        elasticsearchClient = mock(ElasticsearchClient.class);
        knowledgeBaseProperties = new KnowledgeBaseProperties();
        knowledgeBaseProperties.getElasticsearch().setIndexName("kb_chunks_v2");
        documentAclService = mock(DocumentAclService.class);
        retrievalRerankService = mock(RetrievalRerankService.class);
        when(retrievalRerankService.rerank(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        vectorSearchService = new VectorSearchServiceImpl(
                embeddingService,
                kbDocumentMapper,
                elasticsearchClient,
                knowledgeBaseProperties,
                documentAclService,
                retrievalRerankService
        );
    }

    @Test
    @DisplayName("ACL 可读文档范围为空时应直接返回空结果")
    void search_shouldReturnEmpty_whenReadableDocumentIdsEmpty() {
        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of());

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "如何改造 ACL", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(documentAclService, times(1)).listReadableDocumentIds(1001L, 1L);
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(kbDocumentMapper);
    }

    @Test
    @DisplayName("检索应只加载 ACL 可读文档")
    void search_shouldOnlyLoadReadableDocuments() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("如何改造 ACL")).thenReturn(new float[]{0.1f, 0.2f});
        mockEmptyEsSearch();
        

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "如何改造 ACL", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(documentAclService, times(1)).listReadableDocumentIds(1001L, 1L);
        verify(kbDocumentMapper, times(1)).selectList(any());
        verify(embeddingService, times(1)).embedQuery("如何改造 ACL");
        verify(elasticsearchClient, times(2)).search(
                ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                ArgumentMatchers.<Class<KbChunkIndexDocument>>any()
        );
        
    }

    @Test
    @DisplayName("关键词检索结果应被限制在 ACL 文档范围内")
    void search_shouldFilterResultsByDocumentScope() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("ACL 改造")).thenReturn(new float[]{0.1f, 0.2f});
        mockEsSearchResponses(
                buildSearchResponse(List.of()),
                buildSearchResponse(List.of(
                        buildHit(11L, 1L, "ACL 文档", "ACL 改造包括文档权限、检索权限和工具权限", 1.2D)
                ))
        );

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "ACL 改造", 5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getDocumentId());
    }

    @Test
    @DisplayName("即使底层返回脏数据，最终结果也应按 ACL 文档范围过滤")
    void search_shouldDropResultsOutsideAclScope() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("文档内容")).thenReturn(new float[]{0.1f, 0.2f});
        mockEsSearchResponses(
                buildSearchResponse(List.of()),
                buildSearchResponse(List.of(
                        buildHit(11L, 1L, "ACL 文档", "这是允许访问的文档内容", 1.1D),
                        buildHit(12L, 999L, "脏数据文档", "这是不应进入结果的脏数据", 1.0D)
                ))
        );

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "文档内容", 5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getDocumentId());
    }

    @Test
    @DisplayName("无 ACL 可读文档时不应调用 ES 向量检索")
    void search_shouldNotCallEs_whenNoReadableDocuments() throws IOException {
        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of());

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "测试问题", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(elasticsearchClient, never()).search(
                ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                ArgumentMatchers.<Class<KbChunkIndexDocument>>any()
        );

    }

    private KbDocument buildDocument(Long id, Long ownerUserId, String visibility) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setKbId(1L);
        document.setTitle("ACL 文档");
        document.setOwnerUserId(ownerUserId);
        document.setVisibility(visibility);
        document.setDeleted(0);
        document.setEnabled(1);
        document.setParseStatus("SUCCESS");
        return document;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockEmptyEsSearch() throws Exception {
        mockEsSearchResponses(buildSearchResponse(List.of()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockEsSearchResponses(SearchResponse<KbChunkIndexDocument>... responses) throws Exception {
        var stubber = doReturn((SearchResponse) responses[0]);
        for (int i = 1; i < responses.length; i++) {
            stubber = stubber.doReturn((SearchResponse) responses[i]);
        }

        stubber.when(elasticsearchClient).search(
                ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                ArgumentMatchers.<Class<KbChunkIndexDocument>>any()
        );
    }

    @SuppressWarnings("unchecked")
    private SearchResponse<KbChunkIndexDocument> buildSearchResponse(List<Hit<KbChunkIndexDocument>> hits) {
        SearchResponse<KbChunkIndexDocument> response = mock(SearchResponse.class);
        HitsMetadata<KbChunkIndexDocument> hitsMetadata = mock(HitsMetadata.class);

        when(response.hits()).thenReturn(hitsMetadata);
        when(hitsMetadata.hits()).thenReturn(hits);

        return response;
    }

    @SuppressWarnings("unchecked")
    private Hit<KbChunkIndexDocument> buildHit(Long chunkId,
                                               Long documentId,
                                               String title,
                                               String content,
                                               Double score) {
        Hit<KbChunkIndexDocument> hit = mock(Hit.class);
        KbChunkIndexDocument source = KbChunkIndexDocument.builder()
                .chunkId(chunkId)
                .documentId(documentId)
                .title(title)
                .content(content)
                .build();

        when(hit.source()).thenReturn(source);
        when(hit.score()).thenReturn(score);

        return hit;
    }


    @Test
    @DisplayName("检索链路应在 balancing 之后调用 rerank")
    void search_shouldInvokeRerank_afterBalance() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("ACL 改造")).thenReturn(new float[]{0.1f, 0.2f});
        mockEsSearchResponses(
                buildSearchResponse(List.of()),
                buildSearchResponse(List.of(
                        buildHit(11L, 1L, "ACL 文档", "ACL 改造包括文档权限、检索权限和工具权限", 1.2D)
                ))
        );

        vectorSearchService.search(1L, 1001L, "ACL 改造", 5);

        verify(retrievalRerankService, times(1)).rerank(eq("ACL 改造"), any(), any());
    }

    @Test
    @DisplayName("检索链路应消费 rerank 后结果")
    void search_shouldReturnRerankedResults() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("ACL 改造")).thenReturn(new float[]{0.1f, 0.2f});
        mockEsSearchResponses(
                buildSearchResponse(List.of()),
                buildSearchResponse(List.of(
                        buildHit(11L, 1L, "ACL 文档", "原始候选", 1.2D)
                ))
        );

        List<ChunkSearchResult> rerankedResults = List.of(
                ChunkSearchResult.builder()
                        .chunkId(99L)
                        .documentId(1L)
                        .documentTitle("Reranked 文档")
                        .content("rerank 结果")
                        .score(0.99D)
                        .rank(1)
                        .build()
        );

        when(retrievalRerankService.rerank(eq("ACL 改造"), any(), any())).thenReturn(rerankedResults);

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "ACL 改造", 5);

        assertEquals(1, results.size());
        assertEquals(99L, results.get(0).getChunkId());
        assertEquals("Reranked 文档", results.get(0).getDocumentTitle());
    }


}
