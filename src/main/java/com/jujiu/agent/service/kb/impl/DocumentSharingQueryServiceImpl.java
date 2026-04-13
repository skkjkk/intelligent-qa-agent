package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.response.KbDocumentAclGrantItemResponse;
import com.jujiu.agent.model.dto.response.KbDocumentGroupResponse;
import com.jujiu.agent.model.dto.response.KbDocumentSharingOverviewResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbDocumentAcl;
import com.jujiu.agent.model.entity.KbDocumentGroup;
import com.jujiu.agent.model.entity.KbGroup;
import com.jujiu.agent.repository.KbDocumentAclRepository;
import com.jujiu.agent.repository.KbDocumentGroupRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.repository.KbGroupRepository;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.DocumentSharingQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:26
 */
@Service
public class DocumentSharingQueryServiceImpl implements DocumentSharingQueryService {

    private static final String PRINCIPAL_TYPE_USER = "USER";
    private static final String PRINCIPAL_TYPE_GROUP = "GROUP";

    private final KbDocumentRepository kbDocumentRepository;
    private final KbDocumentAclRepository kbDocumentAclRepository;
    private final KbDocumentGroupRepository kbDocumentGroupRepository;
    private final KbGroupRepository kbGroupRepository;
    private final DocumentAclService documentAclService;

    public DocumentSharingQueryServiceImpl(KbDocumentRepository kbDocumentRepository,
                                           KbDocumentAclRepository kbDocumentAclRepository,
                                           KbDocumentGroupRepository kbDocumentGroupRepository,
                                           KbGroupRepository kbGroupRepository,
                                           DocumentAclService documentAclService) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentAclRepository = kbDocumentAclRepository;
        this.kbDocumentGroupRepository = kbDocumentGroupRepository;
        this.kbGroupRepository = kbGroupRepository;
        this.documentAclService = documentAclService;
    }
    @Override
    public KbDocumentSharingOverviewResponse getSharingOverview(Long userId, Long documentId) {
        KbDocument document = requireShareableDocument(userId, documentId);
        
        List<KbDocumentAcl> aclList = kbDocumentAclRepository.selectList(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .orderByAsc(KbDocumentAcl::getCreatedAt)
        );
        List<KbDocumentAclGrantItemResponse> userGrants = aclList.stream()
                .filter(item -> PRINCIPAL_TYPE_USER.equalsIgnoreCase(item.getPrincipalType()))
                .map(this::toGrantItem)
                .toList();

        List<KbDocumentAclGrantItemResponse> groupGrants = aclList.stream()
                .filter(item -> PRINCIPAL_TYPE_GROUP.equalsIgnoreCase(item.getPrincipalType()))
                .map(this::toGrantItem)
                .toList();
        List<KbDocumentGroupResponse> sharedGroups = kbDocumentGroupRepository.selectList(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .orderByAsc(KbDocumentGroup::getCreatedAt)
        ).stream().map(this::toDocumentGroupResponse).toList();

        return KbDocumentSharingOverviewResponse.builder()
                .documentId(document.getId())
                .title(document.getTitle())
                .ownerUserId(document.getOwnerUserId())
                .visibility(document.getVisibility())
                .createdAt(document.getCreatedAt())
                .readable(documentAclService.canRead(userId, document))
                .manageable(documentAclService.canManage(userId, document))
                .shareable(documentAclService.canShare(userId, document))
                .userGrants(userGrants)
                .groupGrants(groupGrants)
                .sharedGroups(sharedGroups)
                .build();
    }

    private KbDocument requireShareableDocument(Long userId, Long documentId) {
        KbDocument document = kbDocumentRepository.selectById(documentId);
        if (document == null || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (!documentAclService.canShare(userId, document)) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        return document;
    }
    
    private KbDocumentAclGrantItemResponse toGrantItem(KbDocumentAcl acl) {
        return KbDocumentAclGrantItemResponse.builder()
                .aclId(acl.getId())
                .principalType(acl.getPrincipalType())
                .principalId(acl.getPrincipalId())
                .permission(acl.getPermission())
                .createdAt(acl.getCreatedAt())
                .build();
    }

    private KbDocumentGroupResponse toDocumentGroupResponse(KbDocumentGroup relation) {
        KbGroup group = kbGroupRepository.selectById(relation.getGroupId());

        return KbDocumentGroupResponse.builder()
                .id(relation.getId())
                .documentId(relation.getDocumentId())
                .groupId(relation.getGroupId())
                .groupName(group == null ? null : group.getName())
                .groupCode(group == null ? null : group.getCode())
                .createdAt(relation.getCreatedAt())
                .build();
    }
    
}
