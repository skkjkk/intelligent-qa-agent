package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.dto.response.DocumentProcessStatusResponse;
import com.jujiu.agent.model.dto.response.KbBatchOperationResponse;
import com.jujiu.agent.model.dto.response.KbDocumentResponse;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.enums.KbDocumentStatus;
import com.jujiu.agent.model.enums.KbProcessStatus;
import com.jujiu.agent.mq.DocumentProcessProducer;
import com.jujiu.agent.repository.KbChunkRepository;
import com.jujiu.agent.repository.KbDocumentGroupRepository;
import com.jujiu.agent.repository.KbDocumentProcessLogRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.DocumentAclAuditService;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.ElasticsearchIndexService;
import com.jujiu.agent.service.kb.EmbeddingService;
import com.jujiu.agent.storage.MinioFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentServiceImplTest {

    private MinioFileService minioFileService;
    private KbDocumentRepository kbDocumentRepository;
    private KbDocumentProcessLogRepository kbDocumentProcessLogRepository;
    private KbChunkRepository kbChunkRepository;
    private ElasticsearchIndexService elasticsearchIndexService;
    private DocumentProcessProducer documentProcessProducer;
    private EmbeddingService embeddingService;
    private DocumentAclService documentAclService;
    private DocumentAclAuditService documentAclAuditService;
    private KbDocumentGroupRepository kbDocumentGroupRepository;
    private KnowledgeBaseProperties knowledgeBaseProperties;
    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        minioFileService = mock(MinioFileService.class);
        kbDocumentRepository = mock(KbDocumentRepository.class);
        kbDocumentProcessLogRepository = mock(KbDocumentProcessLogRepository.class);
        kbChunkRepository = mock(KbChunkRepository.class);
        elasticsearchIndexService = mock(ElasticsearchIndexService.class);
        documentProcessProducer = mock(DocumentProcessProducer.class);
        embeddingService = mock(EmbeddingService.class);
        documentAclService = mock(DocumentAclService.class);
        documentAclAuditService = mock(DocumentAclAuditService.class);
        kbDocumentGroupRepository = mock(KbDocumentGroupRepository.class);
        knowledgeBaseProperties = mock(KnowledgeBaseProperties.class);
        
        documentService = new DocumentServiceImpl(
                minioFileService,
                kbDocumentRepository,
                kbDocumentProcessLogRepository,
                kbChunkRepository,
                elasticsearchIndexService,
                documentProcessProducer,
                embeddingService,
                documentAclService,
                documentAclAuditService,
                kbDocumentGroupRepository,
                knowledgeBaseProperties
        );
    }

    @Test
    @DisplayName("ACL 可读用户应能查询文档详情")
    void getDocument_shouldReturnDocument_whenUserCanRead() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE");
        when(kbDocumentRepository.selectById(1L)).thenReturn(document);
        when(documentAclService.canRead(1001L, document)).thenReturn(true);

        KbDocumentResponse response = documentService.getDocument(1001L, 1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("测试文档", response.getTitle());
        verify(documentAclService, times(1)).canRead(1001L, document);
    }

    @Test
    @DisplayName("无读取权限时查询文档详情应抛异常")
    void getDocument_shouldThrow_whenUserCannotRead() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE");
        when(kbDocumentRepository.selectById(1L)).thenReturn(document);
        when(documentAclService.canRead(1001L, document)).thenReturn(false);

        assertThrows(BusinessException.class, () -> documentService.getDocument(1001L, 1L));

        verify(documentAclService, times(1)).canRead(1001L, document);
        verify(documentAclAuditService, times(1))
                .logAccessDenied(1L, 1001L, "NO_READ_PERMISSION");
    }

    @Test
    @DisplayName("文档列表应返回 ACL 可读文档，而不是只返回 owner 文档")
    void listDocuments_shouldReturnReadableDocuments() {
        KbDocument readableDocument = buildDocument(1L, 2002L, "PRIVATE");

        when(documentAclService.listReadableDocumentIds(1001L, 1L)).thenReturn(Set.of(1L));
        when(kbDocumentRepository.selectList(any())).thenReturn(List.of(readableDocument));

        List<KbDocumentResponse> responses = documentService.listDocuments(1001L, 1L);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).getId());

        verify(documentAclService, times(1)).listReadableDocumentIds(1001L, 1L);
        verify(kbDocumentRepository, times(1)).selectList(any());
    }

    @Test
    @DisplayName("ACL 可读用户应能查询文档处理状态")
    void getDocumentStatus_shouldReturnStatus_whenUserCanRead() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE");
        document.setStatus("SUCCESS");
        document.setParseStatus("SUCCESS");
        document.setIndexStatus("SUCCESS");
        document.setChunkCount(3);

        when(kbDocumentRepository.selectById(1L)).thenReturn(document);
        when(documentAclService.canRead(1001L, document)).thenReturn(true);

        DocumentProcessStatusResponse response = documentService.getDocumentStatus(1001L, 1L);

        assertNotNull(response);
        assertEquals(1L, response.getDocumentId());
        assertEquals("SUCCESS", response.getStatus());

        verify(documentAclService, times(1)).canRead(1001L, document);
    }

    @Test
    @DisplayName("只有可管理用户才能删除文档")
    void deleteDocument_shouldUpdateDeletedFlag_whenUserCanManage() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE");
        when(kbDocumentRepository.selectById(1L)).thenReturn(document);
        when(documentAclService.canManage(1001L, document)).thenReturn(true);

        documentService.deleteDocument(1001L, 1L);

        assertEquals(1, document.getDeleted());
        verify(kbDocumentRepository, times(1)).updateById(document);
        verify(documentAclService, times(1)).canManage(1001L, document);
    }

    @Test
    @DisplayName("仅有 SHARE 或 READ 的用户不应能索引文档")
    void indexDocument_shouldThrow_whenUserCannotManage() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE");
        when(kbDocumentRepository.selectById(1L)).thenReturn(document);
        when(documentAclService.canManage(1001L, document)).thenReturn(false);

        assertThrows(BusinessException.class, () -> documentService.indexDocument(1001L, 1L));

        verify(documentAclService, times(1)).canManage(1001L, document);
        verify(documentAclAuditService, times(1))
                .logAccessDenied(1L, 1001L, "NO_MANAGE_PERMISSION");
        verifyNoInteractions(elasticsearchIndexService);
    }

    @Test
    @DisplayName("可管理用户应能重建索引")
    void rebuildIndex_shouldInvokeDeleteAndReindex_whenUserCanManage() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE");
        document.setEnabled(1);
        document.setParseStatus(KbProcessStatus.SUCCESS.name());
        document.setChunkCount(1);

        KbChunk chunk = buildChunk(11L, 1L);

        when(kbDocumentRepository.selectById(1L)).thenReturn(document);
        when(documentAclService.canManage(1001L, document)).thenReturn(true);
        when(kbChunkRepository.selectList(any())).thenReturn(List.of(chunk), List.of(chunk));
        when(embeddingService.embedDocument(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(elasticsearchIndexService.countByDocumentId(1L)).thenReturn(1L);

        documentService.rebuildIndex(1001L, 1L);

        verify(documentAclService, atLeastOnce()).canManage(1001L, document);
        verify(elasticsearchIndexService, times(1))
                .deleteByDocumentIdAndExcludeChunkIds(eq(1L), eq(List.of(11L)));
        verify(elasticsearchIndexService, times(1)).ensureIndexExists();
        verify(elasticsearchIndexService, times(1)).indexChunk(eq(document), eq(chunk), any());
        verify(elasticsearchIndexService, times(1)).countByDocumentId(1L);
    }

    @Test
    @DisplayName("批量索引应只处理当前用户可管理的文档")
    void indexPendingDocuments_shouldOnlyProcessManageableDocuments() {
        KbDocument manageableDocument1 = buildDocument(1L, 2002L, "PRIVATE");
        manageableDocument1.setIndexStatus(KbProcessStatus.PENDING.name());

        KbDocument manageableDocument2 = buildDocument(2L, 3003L, "PRIVATE");
        manageableDocument2.setIndexStatus(KbProcessStatus.FAILED.name());

        KbDocument readOnlyDocument = buildDocument(3L, 4004L, "PRIVATE");
        readOnlyDocument.setIndexStatus(KbProcessStatus.PENDING.name());

        KbChunk chunk1 = buildChunk(101L, 1L);
        KbChunk chunk2 = buildChunk(102L, 2L);

        when(kbDocumentRepository.selectList(any()))
                .thenReturn(List.of(manageableDocument1, manageableDocument2, readOnlyDocument));

        when(documentAclService.canManage(1001L, manageableDocument1)).thenReturn(true);
        when(documentAclService.canManage(1001L, manageableDocument2)).thenReturn(true);
        when(documentAclService.canManage(1001L, readOnlyDocument)).thenReturn(false);

        when(kbDocumentRepository.selectById(1L)).thenReturn(manageableDocument1);
        when(kbDocumentRepository.selectById(2L)).thenReturn(manageableDocument2);

        when(kbChunkRepository.selectList(any()))
                .thenReturn(List.of(chunk1))
                .thenReturn(List.of(chunk2));

        when(embeddingService.embedDocument(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f});

        KbBatchOperationResponse response = documentService.indexPendingDocuments(1001L);

        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.getFailedDocumentIds().isEmpty());

        verify(documentAclService, atLeastOnce()).canManage(1001L, manageableDocument1);
        verify(documentAclService, atLeastOnce()).canManage(1001L, manageableDocument2);
        verify(documentAclService, times(1)).canManage(1001L, readOnlyDocument);

        verify(elasticsearchIndexService, times(2)).ensureIndexExists();
        verify(elasticsearchIndexService, times(2)).indexChunk(any(KbDocument.class), any(KbChunk.class), any());
        verify(kbDocumentRepository, never()).selectById(3L);
    }

    @Test
    @DisplayName("批量重建应只处理当前用户可管理的失败索引文档")
    void rebuildFailedIndexes_shouldOnlyProcessManageableFailedDocuments() {
        KbDocument manageableFailedDocument1 = buildDocument(11L, 2002L, "PRIVATE");
        manageableFailedDocument1.setIndexStatus(KbProcessStatus.FAILED.name());

        KbDocument manageableFailedDocument2 = buildDocument(12L, 3003L, "PRIVATE");
        manageableFailedDocument2.setIndexStatus(KbProcessStatus.FAILED.name());

        KbDocument readOnlyFailedDocument = buildDocument(13L, 4004L, "PRIVATE");
        readOnlyFailedDocument.setIndexStatus(KbProcessStatus.FAILED.name());

        KbChunk chunk1 = buildChunk(201L, 11L);
        KbChunk chunk2 = buildChunk(202L, 12L);

        when(kbDocumentRepository.selectList(any()))
                .thenReturn(List.of(manageableFailedDocument1, manageableFailedDocument2, readOnlyFailedDocument));

        when(documentAclService.canManage(1001L, manageableFailedDocument1)).thenReturn(true);
        when(documentAclService.canManage(1001L, manageableFailedDocument2)).thenReturn(true);
        when(documentAclService.canManage(1001L, readOnlyFailedDocument)).thenReturn(false);

        when(kbDocumentRepository.selectById(11L)).thenReturn(manageableFailedDocument1);
        when(kbDocumentRepository.selectById(12L)).thenReturn(manageableFailedDocument2);

        when(kbChunkRepository.selectList(any()))
                .thenReturn(List.of(chunk1))
                .thenReturn(List.of(chunk1))
                .thenReturn(List.of(chunk2))
                .thenReturn(List.of(chunk2));

        when(embeddingService.embedDocument(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f});
        when(elasticsearchIndexService.countByDocumentId(11L)).thenReturn(1L);
        when(elasticsearchIndexService.countByDocumentId(12L)).thenReturn(1L);

        KbBatchOperationResponse response = documentService.rebuildFailedIndexes(1001L);

        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.getFailedDocumentIds().isEmpty());

        verify(documentAclService, atLeastOnce()).canManage(1001L, manageableFailedDocument1);
        verify(documentAclService, atLeastOnce()).canManage(1001L, manageableFailedDocument2);
        verify(documentAclService, times(1)).canManage(1001L, readOnlyFailedDocument);

        verify(elasticsearchIndexService, times(1))
                .deleteByDocumentIdAndExcludeChunkIds(eq(11L), eq(List.of(201L)));
        verify(elasticsearchIndexService, times(1))
                .deleteByDocumentIdAndExcludeChunkIds(eq(12L), eq(List.of(202L)));
        verify(elasticsearchIndexService, times(2)).ensureIndexExists();
        verify(elasticsearchIndexService, times(2)).indexChunk(any(KbDocument.class), any(KbChunk.class), any());
        verify(elasticsearchIndexService, times(1)).countByDocumentId(11L);
        verify(elasticsearchIndexService, times(1)).countByDocumentId(12L);

        verify(kbDocumentRepository, never()).selectById(13L);
        verify(elasticsearchIndexService, never()).deleteByDocumentIdAndExcludeChunkIds(eq(13L), any());
    }

    private KbDocument buildDocument(Long id, Long ownerUserId, String visibility) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setKbId(1L);
        document.setTitle("测试文档");
        document.setFileName("test.md");
        document.setFileType("md");
        document.setFileSize(100L);
        document.setOwnerUserId(ownerUserId);
        document.setVisibility(visibility);
        document.setDeleted(0);
        document.setEnabled(1);
        document.setParseStatus(KbProcessStatus.SUCCESS.name());
        document.setIndexStatus(KbProcessStatus.PENDING.name());
        document.setStatus(KbDocumentStatus.SUCCESS.name());
        document.setChunkCount(1);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }

    private KbChunk buildChunk(Long chunkId, Long documentId) {
        KbChunk chunk = new KbChunk();
        chunk.setId(chunkId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent("测试分块内容");
        chunk.setEnabled(1);
        return chunk;
    }
}
