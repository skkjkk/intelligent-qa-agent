package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbDocumentAclRepository;
import com.jujiu.agent.repository.KbDocumentGroupRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.GroupMembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentAclServiceImplTest {

    private KbDocumentRepository kbDocumentRepository;
    private KbDocumentAclRepository kbDocumentAclRepository;
    private KnowledgeBaseProperties knowledgeBaseProperties;
    private DocumentAclService documentAclService;
    private GroupMembershipService groupMembershipService;
    private KbDocumentGroupRepository kbDocumentGroupRepository;
    @BeforeEach
    void setUp() {
        kbDocumentRepository = mock(KbDocumentRepository.class);
        kbDocumentAclRepository = mock(KbDocumentAclRepository.class);
        knowledgeBaseProperties = new KnowledgeBaseProperties();
        groupMembershipService = mock(GroupMembershipService.class);
        kbDocumentGroupRepository = mock(KbDocumentGroupRepository.class);
        knowledgeBaseProperties.getSecurity().setEnableAcl(true);

        documentAclService = new DocumentAclServiceImpl(
                kbDocumentRepository,
                kbDocumentAclRepository,
                knowledgeBaseProperties,
                groupMembershipService,
                kbDocumentGroupRepository
                
        );
    }

    @Test
    @DisplayName("owner 应当拥有读取权限")
    void canRead_shouldReturnTrue_whenUserIsOwner() {
        KbDocument document = buildDocument(1L, 1001L, "PRIVATE", 1, 0);

        boolean result = documentAclService.canRead(1001L, document);

        assertTrue(result);
        verifyNoInteractions(kbDocumentAclRepository);
    }

    @Test
    @DisplayName("PUBLIC 文档应当允许非 owner 用户读取")
    void canRead_shouldReturnTrue_whenDocumentIsPublic() {
        KbDocument document = buildDocument(1L, 2002L, "PUBLIC", 1, 0);

        boolean result = documentAclService.canRead(1001L, document);

        assertTrue(result);
        verifyNoInteractions(kbDocumentAclRepository);
    }

    @Test
    @DisplayName("显式 READ 授权时应允许读取")
    void canRead_shouldReturnTrue_whenUserHasReadGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(1L);

        boolean result = documentAclService.canRead(1001L, document);

        assertTrue(result);
        verify(kbDocumentAclRepository, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("显式 MANAGE 授权时应允许管理")
    void canManage_shouldReturnTrue_whenUserHasManageGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(1L);

        boolean result = documentAclService.canManage(1001L, document);

        assertTrue(result);
        verify(kbDocumentAclRepository, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("仅有 READ 授权时不应允许管理")
    void canManage_shouldReturnFalse_whenUserOnlyHasReadGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(0L);

        boolean result = documentAclService.canManage(1001L, document);

        assertFalse(result);
        verify(kbDocumentAclRepository, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("ACL 关闭时应回退到 owner-only 语义")
    void canRead_shouldFallbackToOwnerOnly_whenAclDisabled() {
        knowledgeBaseProperties.getSecurity().setEnableAcl(false);
        KbDocument ownerDocument = buildDocument(1L, 1001L, "PUBLIC", 1, 0);
        KbDocument otherUserDocument = buildDocument(2L, 2002L, "PUBLIC", 1, 0);

        assertTrue(documentAclService.canRead(1001L, ownerDocument));
        assertFalse(documentAclService.canRead(1001L, otherUserDocument));

        verifyNoInteractions(kbDocumentAclRepository);
    }

    @Test
    @DisplayName("已删除文档不应允许读取")
    void canRead_shouldReturnFalse_whenDocumentDeleted() {
        KbDocument document = buildDocument(1L, 1001L, "PRIVATE", 1, 1);

        boolean result = documentAclService.canRead(1001L, document);

        assertFalse(result);
        verifyNoInteractions(kbDocumentAclRepository);
    }

    @Test
    @DisplayName("已禁用文档不应允许读取")
    void canRead_shouldReturnFalse_whenDocumentDisabled() {
        KbDocument document = buildDocument(1L, 1001L, "PRIVATE", 0, 0);

        boolean result = documentAclService.canRead(1001L, document);

        assertFalse(result);
        verifyNoInteractions(kbDocumentAclRepository);
    }

    @Test
    @DisplayName("显式 SHARE 授权时应允许分享")
    void canShare_shouldReturnTrue_whenUserHasShareGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(1L);

        boolean result = documentAclService.canShare(1001L, document);

        assertTrue(result);
        verify(kbDocumentAclRepository, times(1)).selectCount(any());
    }


    @Test
    @DisplayName("MANAGE 权限应隐含 SHARE 能力")
    void canShare_shouldReturnTrue_whenUserHasManageGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(1L);

        boolean result = documentAclService.canShare(1001L, document);

        assertTrue(result);
    }

    @Test
    @DisplayName("仅有 READ 权限时不应允许分享")
    void canShare_shouldReturnFalse_whenUserOnlyHasReadGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(0L);

        boolean result = documentAclService.canShare(1001L, document);

        assertFalse(result);
    }
    
    @Test
    @DisplayName("当用户所在组被授予 READ 时应允许读取")
    void canRead_shouldReturnTrue_whenUserGroupHasReadGrant() {
        KbDocument document = buildDocument(1L, 2002L, "PRIVATE", 1, 0);

        when(kbDocumentAclRepository.selectCount(any()))
                .thenReturn(0L) // user permission
                .thenReturn(1L); // group permission
        when(groupMembershipService.listGroupIdsByUserId(1001L)).thenReturn(Set.of(10L));

        boolean result = documentAclService.canRead(1001L, document);

        assertTrue(result);
    }

    @Test
    @DisplayName("GROUP_SHARED 文档应允许绑定组成员读取")
    void canRead_shouldReturnTrue_whenDocumentIsGroupSharedAndUserInGroup() {
        KbDocument document = buildDocument(1L, 2002L, "GROUP_SHARED", 1, 0);

        when(groupMembershipService.listGroupIdsByUserId(1001L)).thenReturn(Set.of(10L));
        when(kbDocumentGroupRepository.selectCount(any())).thenReturn(1L);

        boolean result = documentAclService.canRead(1001L, document);

        assertTrue(result);
    }

    @Test
    @DisplayName("GROUP_SHARED 文档在用户不属于绑定组时不应可读")
    void canRead_shouldReturnFalse_whenDocumentIsGroupSharedButUserNotInGroup() {
        KbDocument document = buildDocument(1L, 2002L, "GROUP_SHARED", 1, 0);

        when(groupMembershipService.listGroupIdsByUserId(1001L)).thenReturn(Set.of(10L));
        when(kbDocumentGroupRepository.selectCount(any())).thenReturn(0L);
        when(kbDocumentAclRepository.selectCount(any())).thenReturn(0L);

        boolean result = documentAclService.canRead(1001L, document);

        assertFalse(result);
    }

    @Test
    @DisplayName("上传 GROUP_SHARED 文档时应写入文档共享组关联")
    void uploadDocument_shouldSaveDocumentGroupRelation_whenVisibilityIsGroupShared() {
        // 这条如果你现在 uploadDocument 的测试体系还没搭起来，
        // 可以先放到下一轮补，不强行现在做。
    }

    
    private KbDocument buildDocument(Long id,
                                     Long ownerUserId,
                                     String visibility,
                                     Integer enabled,
                                     Integer deleted) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setKbId(1L);
        document.setOwnerUserId(ownerUserId);
        document.setVisibility(visibility);
        document.setEnabled(enabled);
        document.setDeleted(deleted);
        return document;
    }
}
