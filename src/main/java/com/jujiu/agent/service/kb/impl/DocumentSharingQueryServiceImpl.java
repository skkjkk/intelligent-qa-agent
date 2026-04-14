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
 * 文档共享查询服务实现类。
 * <p>
 * 负责查询文档的共享概况，包括文档基础信息、ACL 用户授权、ACL 组授权
 * 以及已绑定的共享组列表，同时返回当前用户对文档的读/管理/共享权限状态。
 * </p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:26
 */
@Service
public class DocumentSharingQueryServiceImpl implements DocumentSharingQueryService {

    /** ACL 主体类型：用户。 */
    private static final String PRINCIPAL_TYPE_USER = "USER";

    /** ACL 主体类型：用户组。 */
    private static final String PRINCIPAL_TYPE_GROUP = "GROUP";

    /** 文档持久化仓库。 */
    private final KbDocumentRepository kbDocumentRepository;

    /** 文档 ACL 持久化仓库。 */
    private final KbDocumentAclRepository kbDocumentAclRepository;

    /** 文档-用户组关联持久化仓库。 */
    private final KbDocumentGroupRepository kbDocumentGroupRepository;

    /** 用户组持久化仓库。 */
    private final KbGroupRepository kbGroupRepository;

    /** 文档 ACL 服务。 */
    private final DocumentAclService documentAclService;

    /**
     * 构造方法。
     *
     * @param kbDocumentRepository      文档持久化仓库
     * @param kbDocumentAclRepository   文档 ACL 持久化仓库
     * @param kbDocumentGroupRepository 文档-用户组关联持久化仓库
     * @param kbGroupRepository         用户组持久化仓库
     * @param documentAclService        文档 ACL 服务
     */
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
    /**
     * 获取文档共享概况。
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @return 包含文档信息、权限授予及共享组列表的概况响应
     */
    @Override
    public KbDocumentSharingOverviewResponse getSharingOverview(Long userId, Long documentId) {
        // 1. 校验并获取当前用户可共享的文档
        KbDocument document = requireShareableDocument(userId, documentId);

        // 2. 查询文档的所有 ACL 授权记录
        List<KbDocumentAcl> aclList = kbDocumentAclRepository.selectList(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .orderByAsc(KbDocumentAcl::getCreatedAt)
        );

        // 3. 过滤并转换用户级别的 ACL 授权
        List<KbDocumentAclGrantItemResponse> userGrants = aclList.stream()
                .filter(item -> PRINCIPAL_TYPE_USER.equalsIgnoreCase(item.getPrincipalType()))
                .map(this::toGrantItem)
                .toList();

        // 4. 过滤并转换用户组级别的 ACL 授权
        List<KbDocumentAclGrantItemResponse> groupGrants = aclList.stream()
                .filter(item -> PRINCIPAL_TYPE_GROUP.equalsIgnoreCase(item.getPrincipalType()))
                .map(this::toGrantItem)
                .toList();

        // 5. 查询文档已绑定的共享组列表并转换为响应对象
        List<KbDocumentGroupResponse> sharedGroups = kbDocumentGroupRepository.selectList(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .orderByAsc(KbDocumentGroup::getCreatedAt)
        ).stream().map(this::toDocumentGroupResponse).toList();

        // 6. 组装共享概况响应对象
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

    /**
     * 校验并获取可共享的文档。
     * <p>
     * 要求文档存在且未被删除，同时当前用户具备共享权限。
     * </p>
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @return 校验通过的文档实体
     * @throws BusinessException 当文档不存在或无共享权限时抛出
     */
    private KbDocument requireShareableDocument(Long userId, Long documentId) {
        // 1. 查询文档基础信息
        KbDocument document = kbDocumentRepository.selectById(documentId);

        // 2. 校验文档是否存在且未被删除
        if (document == null || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }

        // 3. 校验当前用户是否具备共享权限
        if (!documentAclService.canShare(userId, document)) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }

        return document;
    }
    
    /**
     * 将 ACL 实体转换为授权项响应对象。
     *
     * @param acl 文档 ACL 实体
     * @return 授权项响应对象
     */
    private KbDocumentAclGrantItemResponse toGrantItem(KbDocumentAcl acl) {
        // 1. 使用 Builder 组装授权项响应
        return KbDocumentAclGrantItemResponse.builder()
                .aclId(acl.getId())
                .principalType(acl.getPrincipalType())
                .principalId(acl.getPrincipalId())
                .permission(acl.getPermission())
                .createdAt(acl.getCreatedAt())
                .build();
    }

    /**
     * 将文档-用户组关联实体转换为共享组响应对象。
     *
     * @param relation 文档-用户组关联实体
     * @return 共享组响应对象
     */
    private KbDocumentGroupResponse toDocumentGroupResponse(KbDocumentGroup relation) {
        // 1. 查询关联的用户组详细信息
        KbGroup group = kbGroupRepository.selectById(relation.getGroupId());

        // 2. 使用 Builder 组装共享组响应
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
