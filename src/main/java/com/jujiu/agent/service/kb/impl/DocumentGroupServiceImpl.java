package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.request.BindDocumentGroupRequest;
import com.jujiu.agent.model.dto.response.KbDocumentGroupResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbDocumentGroup;
import com.jujiu.agent.model.entity.KbGroup;
import com.jujiu.agent.repository.KbDocumentGroupRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.repository.KbGroupRepository;
import com.jujiu.agent.service.kb.DocumentAclAuditService;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.DocumentGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:08
 */
@Service
@Slf4j
public class DocumentGroupServiceImpl implements DocumentGroupService {
    private static final String VISIBILITY_GROUP_SHARED = "GROUP_SHARED";

    private final KbDocumentRepository kbDocumentRepository;
    private final KbDocumentGroupRepository kbDocumentGroupRepository;
    private final KbGroupRepository kbGroupRepository;
    private final DocumentAclService documentAclService;
    private final DocumentAclAuditService documentAclAuditService;

    public DocumentGroupServiceImpl(KbDocumentRepository kbDocumentRepository,
                                    KbDocumentGroupRepository kbDocumentGroupRepository,
                                    KbGroupRepository kbGroupRepository,
                                    DocumentAclService documentAclService,
                                    DocumentAclAuditService documentAclAuditService) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentGroupRepository = kbDocumentGroupRepository;
        this.kbGroupRepository = kbGroupRepository;
        this.documentAclService = documentAclService;
        this.documentAclAuditService = documentAclAuditService;
    }

    @Override
    public List<KbDocumentGroupResponse> listDocumentGroups(Long userId, Long documentId) {
        KbDocument document = requireShareableDocument(userId, documentId);
        List<KbDocumentGroup> relations = kbDocumentGroupRepository.selectList(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, document.getId())
                        .orderByAsc(KbDocumentGroup::getCreatedAt)
        );
        if (relations.isEmpty()) {
            return List.of();
        }

        return relations.stream().map(relation -> {
            KbGroup group = kbGroupRepository.selectById(relation.getGroupId());
            return KbDocumentGroupResponse.builder()
                    .id(relation.getId())
                    .documentId(relation.getDocumentId())
                    .groupId(relation.getGroupId())
                    .groupName(group == null ? null : group.getName())
                    .groupCode(group == null ? null : group.getCode())
                    .createdAt(relation.getCreatedAt())
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public void bindDocumentGroup(Long userId, Long documentId, BindDocumentGroupRequest request) {
        KbDocument document = requireShareableDocument(userId, documentId);
        validateGroupSharedDocument(document);

        KbGroup group = requireGroup(request.getGroupId());

        KbDocumentGroup existing = kbDocumentGroupRepository.selectOne(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .eq(KbDocumentGroup::getGroupId, group.getId())
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        KbDocumentGroup relation = new KbDocumentGroup();
        relation.setDocumentId(documentId);
        relation.setGroupId(group.getId());
        relation.setCreatedAt(LocalDateTime.now());
        kbDocumentGroupRepository.insert(relation);

        documentAclAuditService.logGroupBind(
                documentId,
                userId,
                group.getId()
        );
    }

    @Override
    @Transactional
    public void unbindDocumentGroup(Long userId, Long documentId, Long groupId) {
        KbDocument document = requireShareableDocument(userId, documentId);
        validateGroupSharedDocument(document);
        requireGroup(groupId);

        int deleted = kbDocumentGroupRepository.delete(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .eq(KbDocumentGroup::getGroupId, groupId)
        );

        if (deleted > 0) {
            documentAclAuditService.logGroupUnbind(
                    documentId,
                    userId,
                    groupId
            );
        }
    }

    private KbDocument requireShareableDocument(Long userId, Long documentId) {
        KbDocument document = kbDocumentRepository.selectById(documentId);
        if (document == null || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (!documentAclService.canShare(userId, document)) {
            documentAclAuditService.logAccessDenied(documentId, userId, "NO_SHARE_PERMISSION");
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private void validateGroupSharedDocument(KbDocument document) {
        if (!VISIBILITY_GROUP_SHARED.equalsIgnoreCase(document.getVisibility())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "仅 GROUP_SHARED 文档可绑定共享组");
        }
    }

    private KbGroup requireGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "groupId 不能为空");
        }

        KbGroup group = kbGroupRepository.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "group 不存在");
        }
        return group;
    }
}
