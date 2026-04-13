package com.jujiu.agent.service.kb.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbChunkRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.search.KbChunkIndexDocument;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 14:42
 */
public class VectorSearchServiceImplTest {
    private EmbeddingService embeddingService;
    private KbDocumentRepository kbDocumentRepository;
    private KbChunkRepository kbChunkRepository;
    private ElasticsearchClient elasticsearchClient;
    private KnowledgeBaseProperties knowledgeBaseProperties;
    private DocumentAclService documentAclService;
    private VectorSearchServiceImpl vectorSearchService;

    @BeforeEach
    void setUp() {
        embeddingService = mock(EmbeddingService.class);
        kbDocumentRepository = mock(KbDocumentRepository.class);
        kbChunkRepository = mock(KbChunkRepository.class);
        elasticsearchClient = mock(ElasticsearchClient.class);
        knowledgeBaseProperties = new KnowledgeBaseProperties();
        knowledgeBaseProperties.getElasticsearch().setIndexName("kb_chunks_v2");
        documentAclService = mock(DocumentAclService.class);

        vectorSearchService = new VectorSearchServiceImpl(
                embeddingService,
                kbDocumentRepository,
                kbChunkRepository,
                elasticsearchClient,
                knowledgeBaseProperties,
                documentAclService
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
        verifyNoInteractions(kbDocumentRepository);
        verifyNoInteractions(kbChunkRepository);
    }

    @Test
    @DisplayName("检索应只在 ACL 可读文档范围内加载文档")
    void search_shouldOnlyLoadReadableDocuments() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentRepository.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("如何改造 ACL")).thenReturn(new float[]{0.1f, 0.2f});
        mockEmptyEsSearch();
        when(kbChunkRepository.selectList(any())).thenReturn(List.of());

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "如何改造 ACL", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }



    @Test
    @DisplayName("关键词检索结果应被限制在 ACL 文档范围内")
    void search_shouldFilterResultsByDocumentScope() throws Exception {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");
        KbChunk chunk = new KbChunk();
        chunk.setId(11L);
        chunk.setDocumentId(1L);
        chunk.setChunkIndex(0);
        chunk.setContent("ACL 改造包括文档权限、检索权限和工具权限");
        chunk.setCharCount(30);
        chunk.setEnabled(1);

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentRepository.selectList(any())).thenReturn(List.of(readableDocument));
        when(embeddingService.embedQuery("ACL 改造")).thenReturn(new float[]{0.1f, 0.2f});
        mockEmptyEsSearch();
        when(kbChunkRepository.selectList(any())).thenReturn(List.of(chunk));

        List<ChunkSearchResult> results = vectorSearchService.search(1L, 1001L, "ACL 改造", 5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getDocumentId());
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

    @SuppressWarnings("unchecked")
    private void mockEmptyEsSearch() throws IOException {
        SearchResponse<KbChunkIndexDocument> response = mock(SearchResponse.class);
        HitsMetadata<KbChunkIndexDocument> hitsMetadata = mock(HitsMetadata.class);

        when(response.hits()).thenReturn(hitsMetadata);
        when(hitsMetadata.hits()).thenReturn(List.of());

        when(elasticsearchClient.search(
                any(Function.class),
                eq(KbChunkIndexDocument.class)
        )).thenReturn(response);
    }


}
