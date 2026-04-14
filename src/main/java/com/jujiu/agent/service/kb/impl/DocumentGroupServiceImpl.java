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
 * 文档共享组服务实现类。
 * <p>
 * 负责管理文档与用户组之间的绑定关系，仅对 {@code GROUP_SHARED} 可见性文档生效。
 * 提供查询、绑定、解绑共享组的能力，并进行权限校验与审计日志记录。
 * </p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:08
 */
@Service
@Slf4j
public class DocumentGroupServiceImpl implements DocumentGroupService {

    /** 文档可见性：组内共享。 */
    private static final String VISIBILITY_GROUP_SHARED = "GROUP_SHARED";

    /** 文档持久化仓库。 */
    private final KbDocumentRepository kbDocumentRepository;

    /** 文档-用户组关联持久化仓库。 */
    private final KbDocumentGroupRepository kbDocumentGroupRepository;

    /** 用户组持久化仓库。 */
    private final KbGroupRepository kbGroupRepository;

    /** 文档 ACL 服务。 */
    private final DocumentAclService documentAclService;

    /** 文档 ACL 审计服务。 */
    private final DocumentAclAuditService documentAclAuditService;

    /**
     * 构造方法。
     *
     * @param kbDocumentRepository      文档持久化仓库
     * @param kbDocumentGroupRepository 文档-用户组关联持久化仓库
     * @param kbGroupRepository         用户组持久化仓库
     * @param documentAclService        文档 ACL 服务
     * @param documentAclAuditService   文档 ACL 审计服务
     */
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

    /**
     * 查询文档已绑定的共享组列表。
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @return 共享组响应列表
     */
    @Override
    public List<KbDocumentGroupResponse> listDocumentGroups(Long userId, Long documentId) {
        // 1. 校验当前用户是否具备共享该文档的权限
        KbDocument document = requireShareableDocument(userId, documentId);

        // 2. 查询文档与用户组的关联记录
        List<KbDocumentGroup> relations = kbDocumentGroupRepository.selectList(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, document.getId())
                        .orderByAsc(KbDocumentGroup::getCreatedAt)
        );

        // 3. 若无关联记录则返回空列表
        if (relations.isEmpty()) {
            return List.of();
        }

        // 4. 遍历关联记录并组装响应对象
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

    /**
     * 将文档与指定用户组绑定（共享）。
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @param request    绑定请求，包含 groupId
     */
    @Override
    @Transactional
    public void bindDocumentGroup(Long userId, Long documentId, BindDocumentGroupRequest request) {
        // 1. 校验当前用户是否具备共享该文档的权限
        KbDocument document = requireShareableDocument(userId, documentId);

        // 2. 校验文档可见性是否为 GROUP_SHARED
        validateGroupSharedDocument(document);

        // 3. 校验并获取目标用户组
        KbGroup group = requireGroup(request.getGroupId());

        // 4. 查询是否已存在相同绑定关系
        KbDocumentGroup existing = kbDocumentGroupRepository.selectOne(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .eq(KbDocumentGroup::getGroupId, group.getId())
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        // 5. 构建并插入新的绑定关系
        KbDocumentGroup relation = new KbDocumentGroup();
        relation.setDocumentId(documentId);
        relation.setGroupId(group.getId());
        relation.setCreatedAt(LocalDateTime.now());
        kbDocumentGroupRepository.insert(relation);

        // 6. 记录绑定审计日志
        documentAclAuditService.logGroupBind(
                documentId,
                userId,
                group.getId()
        );
    }

    /**
     * 解除文档与指定用户组的绑定关系。
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @param groupId    用户组 ID
     */
    @Override
    @Transactional
    public void unbindDocumentGroup(Long userId, Long documentId, Long groupId) {
        // 1. 校验当前用户是否具备共享该文档的权限
        KbDocument document = requireShareableDocument(userId, documentId);

        // 2. 校验文档可见性是否为 GROUP_SHARED
        validateGroupSharedDocument(document);

        // 3. 校验目标用户组是否存在
        requireGroup(groupId);

        // 4. 执行删除绑定关系
        int deleted = kbDocumentGroupRepository.delete(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .eq(KbDocumentGroup::getGroupId, groupId)
        );

        // 5. 若实际删除了记录，则记录解绑审计日志
        if (deleted > 0) {
            documentAclAuditService.logGroupUnbind(
                    documentId,
                    userId,
                    groupId
            );
        }
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
            documentAclAuditService.logAccessDenied(documentId, userId, "NO_SHARE_PERMISSION");
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }

        return document;
    }

    /**
     * 校验文档可见性是否为 {@code GROUP_SHARED}。
     *
     * @param document 文档实体
     * @throws BusinessException 当文档可见性不符合要求时抛出
     */
    private void validateGroupSharedDocument(KbDocument document) {
        // 1. 校验可见性是否为 GROUP_SHARED
        if (!VISIBILITY_GROUP_SHARED.equalsIgnoreCase(document.getVisibility())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "仅 GROUP_SHARED 文档可绑定共享组");
        }
    }

    /**
     * 校验并获取用户组。
     *
     * @param groupId 用户组 ID
     * @return 校验通过的用户组实体
     * @throws BusinessException 当 groupId 为空或组不存在时抛出
     */
    private KbGroup requireGroup(Long groupId) {
        // 1. 校验 groupId 是否有效
        if (groupId == null || groupId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "groupId 不能为空");
        }

        // 2. 查询用户组信息
        KbGroup group = kbGroupRepository.selectById(groupId);

        // 3. 校验用户组是否存在
        if (group == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "group 不存在");
        }

        return group;
    }
}
