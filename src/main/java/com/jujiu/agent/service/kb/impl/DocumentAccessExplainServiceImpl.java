package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.model.dto.response.KbDocumentAccessExplainResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbDocumentAcl;
import com.jujiu.agent.model.entity.KbDocumentGroup;
import com.jujiu.agent.repository.KbDocumentAclRepository;
import com.jujiu.agent.repository.KbDocumentGroupRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.DocumentAccessExplainService;
import com.jujiu.agent.service.kb.GroupMembershipService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:33
 */
@Service
public class DocumentAccessExplainServiceImpl implements DocumentAccessExplainService {
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_GROUP_SHARED = "GROUP_SHARED";

    private static final String PRINCIPAL_TYPE_USER = "USER";
    private static final String PRINCIPAL_TYPE_GROUP = "GROUP";

    private static final String PERMISSION_READ = "READ";
    private static final String PERMISSION_MANAGE = "MANAGE";
    private static final String PERMISSION_SHARE = "SHARE";

    private final KbDocumentRepository kbDocumentRepository;
    private final KbDocumentAclRepository kbDocumentAclRepository;
    private final KbDocumentGroupRepository kbDocumentGroupRepository;
    private final GroupMembershipService groupMembershipService;

    public DocumentAccessExplainServiceImpl(KbDocumentRepository kbDocumentRepository,
                                            KbDocumentAclRepository kbDocumentAclRepository,
                                            KbDocumentGroupRepository kbDocumentGroupRepository,
                                            GroupMembershipService groupMembershipService) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentAclRepository = kbDocumentAclRepository;
        this.kbDocumentGroupRepository = kbDocumentGroupRepository;
        this.groupMembershipService = groupMembershipService;
    }
    @Override
    public KbDocumentAccessExplainResponse explainAccess(Long userId, Long documentId) {
        KbDocument document = kbDocumentRepository.selectById(documentId);
        if (document == null) {
            return build(documentId, userId, false, "DOCUMENT_NOT_FOUND", "文档不存在");
        }
        
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            return build(documentId, userId, false, "DOCUMENT_DELETED", "文档已删除");
        }
        
        if (document.getEnabled() != null && document.getEnabled() != 1) {
            return build(documentId, userId, false, "DOCUMENT_DISABLED", "文档已禁用");
        }
        
        if (userId != null && userId.equals(document.getOwnerUserId())) {
            return build(documentId, userId, true, "OWNER", "当前用户是文档 owner");
        }
        
        if (VISIBILITY_PUBLIC.equalsIgnoreCase(document.getVisibility())) {
            return build(documentId, userId, true, "PUBLIC", "文档为 PUBLIC，所有登录用户可读");
        }
        
        if (hasDirectUserReadLikePermission(documentId, userId)) {
            return build(documentId, userId, true, "DIRECT_USER_GRANT", "当前用户被直接授予文档访问权限");
        }

        if (hasDirectGroupReadLikePermission(documentId, userId)) {
            return build(documentId, userId, true, "DIRECT_GROUP_GRANT", "当前用户所属组被直接授予文档访问权限");
        }
        
        if (VISIBILITY_GROUP_SHARED.equalsIgnoreCase(document.getVisibility())
                && isGroupSharedVisible(documentId, userId)) {
            return build(documentId, userId, true, "GROUP_SHARED_MEMBER", "当前用户属于文档共享组成员");
        }
        
        return build(documentId, userId, false, "NO_MATCHED_RULE", "当前用户未命中任何可见规则");
    }

    private boolean hasDirectUserReadLikePermission(Long documentId, Long userId) {
        if (userId == null) {
            return false;
        }

        Long count = kbDocumentAclRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, PRINCIPAL_TYPE_USER)
                        .eq(KbDocumentAcl::getPrincipalId, String.valueOf(userId))
                        .in(KbDocumentAcl::getPermission, List.of(PERMISSION_READ, PERMISSION_MANAGE, PERMISSION_SHARE))
        );

        return count != null && count > 0;
    }

    private boolean hasDirectGroupReadLikePermission(Long documentId, Long userId) {
        if (userId == null) {
            return false;
        }

        Set<Long> groupIds = groupMembershipService.listGroupIdsByUserId(userId);
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }

        Long count = kbDocumentAclRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, PRINCIPAL_TYPE_GROUP)
                        .in(KbDocumentAcl::getPrincipalId, groupIds.stream().map(String::valueOf).toList())
                        .in(KbDocumentAcl::getPermission, List.of(PERMISSION_READ, PERMISSION_MANAGE, PERMISSION_SHARE))
        );

        return count != null && count > 0;
    }

    private boolean isGroupSharedVisible(Long documentId, Long userId) {
        if (userId == null) {
            return false;
        }

        Set<Long> groupIds = groupMembershipService.listGroupIdsByUserId(userId);
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }

        Long count = kbDocumentGroupRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .in(KbDocumentGroup::getGroupId, groupIds)
        );

        return count != null && count > 0;
    }

    private KbDocumentAccessExplainResponse build(Long documentId,
                                                  Long userId,
                                                  Boolean visible,
                                                  String reason,
                                                  String detail) {
        return KbDocumentAccessExplainResponse.builder()
                .documentId(documentId)
                .userId(userId)
                .visible(visible)
                .reason(reason)
                .detail(detail)
                .build();
    }
}
