package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.module.kb.application.service.impl.DocumentAclManageServiceImpl;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.api.request.GrantDocumentAclRequest;
import com.jujiu.agent.module.kb.api.response.KbDocumentAclResponse;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.domain.entity.KbDocumentAcl;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentAclMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.application.service.DocumentAclAuditService;
import com.jujiu.agent.module.kb.application.service.DocumentAclService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentAclManageServiceImplTest {

    private KbDocumentMapper kbDocumentMapper;
    private KbDocumentAclMapper kbDocumentAclMapper;
    private DocumentAclService documentAclService;
    private DocumentAclAuditService documentAclAuditService;
    private DocumentAclManageServiceImpl documentAclManageService;

    @BeforeEach
    void setUp() {
        kbDocumentMapper = mock(KbDocumentMapper.class);
        kbDocumentAclMapper = mock(KbDocumentAclMapper.class);
        documentAclService = mock(DocumentAclService.class);
        documentAclAuditService = mock(DocumentAclAuditService.class);

        documentAclManageService = new DocumentAclManageServiceImpl(
                kbDocumentMapper,
                kbDocumentAclMapper,
                documentAclService,
                documentAclAuditService
        );
    }

    @Test
    @DisplayName("有 SHARE 权限时应能查询文档 ACL 列表")
    void listDocumentAcl_shouldReturnAclList_whenUserCanShare() {
        KbDocument document = buildDocument(1L, 2001L);
        KbDocumentAcl acl = KbDocumentAcl.builder()
                .id(11L)
                .documentId(1L)
                .principalType("USER")
                .principalId("3001")
                .permission("READ")
                .createdAt(LocalDateTime.now())
                .build();

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.selectList(any())).thenReturn(List.of(acl));

        List<KbDocumentAclResponse> result = documentAclManageService.listDocumentAcl(1001L, 1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals("USER", result.get(0).getPrincipalType());
        assertEquals("3001", result.get(0).getPrincipalId());
        assertEquals("READ", result.get(0).getPermission());

        verify(documentAclService, times(1)).canShare(1001L, document);
    }

    @Test
    @DisplayName("无 SHARE 权限时查询文档 ACL 列表应抛异常")
    void listDocumentAcl_shouldThrow_whenUserCannotShare() {
        KbDocument document = buildDocument(1L, 2001L);

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentAclManageService.listDocumentAcl(1001L, 1L)
        );

        assertTrue(exception.getMessage().contains("文档不存在"));

        verify(documentAclAuditService, times(1))
                .logAccessDenied(1L, 1001L, "NO_SHARE_PERMISSION");
        verifyNoInteractions(kbDocumentAclMapper);
    }

    @Test
    @DisplayName("有 SHARE 权限时应能授予 USER 的 READ 权限")
    void grantDocumentAcl_shouldInsertUserAcl_whenUserCanShare() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("USER", "3001", "READ");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.selectOne(any())).thenReturn(null);

        documentAclManageService.grantDocumentAcl(1001L, 1L, request);

        verify(kbDocumentAclMapper, times(1)).insert(any());
        verify(documentAclAuditService, times(1))
                .logAclGrant(1L, 1001L, "USER", "3001", "READ");
    }

    @Test
    @DisplayName("应允许给 GROUP 主体授予权限")
    void grantDocumentAcl_shouldInsertAcl_whenPrincipalTypeIsGroup() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("GROUP", "10", "READ");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.selectOne(any())).thenReturn(null);

        documentAclManageService.grantDocumentAcl(1001L, 1L, request);

        verify(kbDocumentAclMapper, times(1)).insert(any());
        verify(documentAclAuditService, times(1))
                .logAclGrant(1L, 1001L, "GROUP", "10", "READ");
    }

    @Test
    @DisplayName("应支持授予 SHARE 权限")
    void grantDocumentAcl_shouldSupportSharePermission() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("USER", "3001", "SHARE");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.selectOne(any())).thenReturn(null);

        documentAclManageService.grantDocumentAcl(1001L, 1L, request);

        verify(kbDocumentAclMapper, times(1)).insert(any());
        verify(documentAclAuditService, times(1))
                .logAclGrant(1L, 1001L, "USER", "3001", "SHARE");
    }

    @Test
    @DisplayName("重复授权时应幂等返回")
    void grantDocumentAcl_shouldBeIdempotent_whenAclAlreadyExists() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("USER", "3001", "READ");
        KbDocumentAcl existing = KbDocumentAcl.builder()
                .id(99L)
                .documentId(1L)
                .principalType("USER")
                .principalId("3001")
                .permission("READ")
                .build();

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.selectOne(any())).thenReturn(existing);

        documentAclManageService.grantDocumentAcl(1001L, 1L, request);

        verify(kbDocumentAclMapper, times(1)).selectOne(any());
        verify(kbDocumentAclMapper, never()).insert(any());
        verify(documentAclAuditService, never())
                .logAclGrant(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("给 owner 本人授权时应直接返回")
    void grantDocumentAcl_shouldReturnDirectly_whenPrincipalIsOwner() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("USER", "2001", "rebuildFailedIndexes 是做什么的");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);

        documentAclManageService.grantDocumentAcl(1001L, 1L, request);

        verify(kbDocumentAclMapper, never()).selectOne(any());
        verify(kbDocumentAclMapper, never()).insert(any());
        verify(documentAclAuditService, never())
                .logAclGrant(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("principalType 非法时授权应抛异常")
    void grantDocumentAcl_shouldThrow_whenPrincipalTypeInvalid() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("ROLE", "3001", "READ");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentAclManageService.grantDocumentAcl(1001L, 1L, request)
        );

        assertTrue(exception.getMessage().contains("当前仅支持 USER 或 GROUP 主体类型"));
        verify(kbDocumentAclMapper, never()).insert(any());
    }

    @Test
    @DisplayName("permission 非法时授权应抛异常")
    void grantDocumentAcl_shouldThrow_whenPermissionInvalid() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("USER", "3001", "DELETE");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentAclManageService.grantDocumentAcl(1001L, 1L, request)
        );

        assertTrue(exception.getMessage().contains("当前仅支持 READ、rebuildFailedIndexes 是做什么的 或 SHARE 权限"));
        verify(kbDocumentAclMapper, never()).insert(any());
    }

    @Test
    @DisplayName("无 SHARE 权限时授权应抛异常")
    void grantDocumentAcl_shouldThrow_whenUserCannotShare() {
        KbDocument document = buildDocument(1L, 2001L);
        GrantDocumentAclRequest request = new GrantDocumentAclRequest("USER", "3001", "READ");

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentAclManageService.grantDocumentAcl(1001L, 1L, request)
        );

        assertTrue(exception.getMessage().contains("文档不存在"));
        verify(documentAclAuditService, times(1))
                .logAccessDenied(1L, 1001L, "NO_SHARE_PERMISSION");
        verify(kbDocumentAclMapper, never()).insert(any());
    }

    @Test
    @DisplayName("有 SHARE 权限时应能回收文档 ACL")
    void revokeDocumentAcl_shouldDeleteAcl_whenUserCanShare() {
        KbDocument document = buildDocument(1L, 2001L);

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.delete(any())).thenReturn(1);

        documentAclManageService.revokeDocumentAcl(1001L, 1L, "USER", "3001", "READ");

        verify(kbDocumentAclMapper, times(1)).delete(any());
        verify(documentAclAuditService, times(1))
                .logAclRevoke(1L, 1001L, "USER", "3001", "READ");
    }

    @Test
    @DisplayName("回收 0 条记录时不应写 ACL_REVOKE 审计")
    void revokeDocumentAcl_shouldNotLogAudit_whenNothingDeleted() {
        KbDocument document = buildDocument(1L, 2001L);

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(true);
        when(kbDocumentAclMapper.delete(any())).thenReturn(0);

        documentAclManageService.revokeDocumentAcl(1001L, 1L, "USER", "3001", "READ");

        verify(kbDocumentAclMapper, times(1)).delete(any());
        verify(documentAclAuditService, never())
                .logAclRevoke(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("无 SHARE 权限时回收授权应抛异常")
    void revokeDocumentAcl_shouldThrow_whenUserCannotShare() {
        KbDocument document = buildDocument(1L, 2001L);

        when(kbDocumentMapper.selectById(1L)).thenReturn(document);
        when(documentAclService.canShare(1001L, document)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentAclManageService.revokeDocumentAcl(1001L, 1L, "USER", "3001", "READ")
        );

        assertTrue(exception.getMessage().contains("文档不存在"));
        verify(documentAclAuditService, times(1))
                .logAccessDenied(1L, 1001L, "NO_SHARE_PERMISSION");
        verify(kbDocumentAclMapper, never()).delete(any());
    }

    @Test
    @DisplayName("文档不存在时回收授权应抛异常")
    void revokeDocumentAcl_shouldThrow_whenDocumentNotFound() {
        when(kbDocumentMapper.selectById(1L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentAclManageService.revokeDocumentAcl(1001L, 1L, "USER", "3001", "READ")
        );

        assertTrue(exception.getMessage().contains("文档不存在"));
        verifyNoInteractions(documentAclService);
        verify(kbDocumentAclMapper, never()).delete(any());
    }

    private KbDocument buildDocument(Long id, Long ownerUserId) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setKbId(1L);
        document.setTitle("ACL 测试文档");
        document.setOwnerUserId(ownerUserId);
        document.setVisibility("PRIVATE");
        document.setEnabled(1);
        document.setDeleted(0);
        return document;
    }
}
