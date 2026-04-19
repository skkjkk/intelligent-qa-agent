package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbDocumentAcl;
import com.jujiu.agent.model.entity.KbDocumentGroup;
import com.jujiu.agent.repository.KbDocumentAclRepository;
import com.jujiu.agent.repository.KbDocumentGroupRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.DocumentAclService;
import com.jujiu.agent.service.kb.GroupMembershipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档 ACL 服务实现类。提供文档访问控制列表的查询与权限校验能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 13:40
 */
@Service
@Slf4j
public class DocumentAclServiceImpl implements DocumentAclService {
    /** 公开可见性标识。 */
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    /** 读取权限标识。 */
    private static final String PERMISSION_READ = "READ";
    /** 管理权限标识。 */
    private static final String PERMISSION_MANAGE = "rebuildFailedIndexes 是做什么的";
    /** 分享权限标识。 */
    private static final String PERMISSION_SHARE = "SHARE";
    /** 主体类型：用户。 */
    private static final String PRINCIPAL_TYPE_USER = "USER";
    /** 主体类型：组。 */
    private static final String PRINCIPAL_TYPE_GROUP = "GROUP";
    /** 文档可见性标识：组共享。 */
    private static final String VISIBILITY_GROUP_SHARED = "GROUP_SHARED";

    /** 知识库文档仓储。 */
    private final KbDocumentRepository kbDocumentRepository;
    /** 文档 ACL 仓储。 */
    private final KbDocumentAclRepository kbDocumentAclRepository;
    /** 知识库配置属性。 */
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    /** 组成员服务。 */
    private final GroupMembershipService groupMembershipService;
    /** 知识库文档组仓储。 */
    private final KbDocumentGroupRepository kbDocumentGroupRepository;
    /**
     * 构造方法。
     *
     * @param kbDocumentRepository     知识库文档仓储
     * @param kbDocumentAclRepository  文档 ACL 仓储
     * @param knowledgeBaseProperties  知识库配置属性
     * @param groupMembershipService 组成员服务
     */
    public DocumentAclServiceImpl(
            KbDocumentRepository kbDocumentRepository,
            KbDocumentAclRepository kbDocumentAclRepository, 
            KnowledgeBaseProperties knowledgeBaseProperties,
            GroupMembershipService groupMembershipService,
            KbDocumentGroupRepository kbDocumentGroupRepository) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentAclRepository = kbDocumentAclRepository;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.groupMembershipService = groupMembershipService;
        this.kbDocumentGroupRepository = kbDocumentGroupRepository;
    }
    
    /**
     * 判断是否启用了 ACL。
     *
     * @return 如果启用了 ACL 则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean isAclEnabled() {
        return knowledgeBaseProperties.getSecurity() != null
                && Boolean.TRUE.equals(knowledgeBaseProperties.getSecurity().getEnableAcl());
    }
    
    /**
     * 判断指定用户是否拥有对文档的读取权限。
     *
     * @param userId   用户 ID
     * @param document 知识库文档
     * @return 如果用户可读取该文档则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean canRead(Long userId, KbDocument document) {
        if (userId == null || document == null) {
            return false;
        }
        
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            return false;
        }
        
        if (document.getEnabled() != null && document.getEnabled() != 1) {
            return false;
        }
        
        if (!isAclEnabled()) {
            return userId.equals(document.getOwnerUserId());
        }
        
        if (userId.equals(document.getOwnerUserId())) {
            return true;
        }

        if (VISIBILITY_PUBLIC.equalsIgnoreCase(document.getVisibility())) {
            return true;
        }
        if (VISIBILITY_GROUP_SHARED.equalsIgnoreCase(document.getVisibility())
                && isSharedToAnyUserGroup(document.getId(), userId)) {
            return true;
        }

        return hasPermission(document.getId(), userId, List.of(PERMISSION_READ, PERMISSION_MANAGE));
    }

    /**判定文档是否被指定用户组所共享
     * 
     * @param documentId 文档ID
     * @param userId 用户ID
     * @return 返回 {@code true} 表示指定用户组已共享该文档，否则返回 {@code false}
     */
    private boolean isSharedToAnyUserGroup(Long documentId, Long userId) {
        Set<Long> groupIds = groupMembershipService.listGroupIdsByUserId(userId);
        if (groupIds == null || groupIds.isEmpty()) {
            return  false;
        }

        Long count = kbDocumentGroupRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .eq(KbDocumentGroup::getGroupId, groupIds)
        );
        
        return count != null && count > 0;
    }

    /**
     * 判断指定用户是否拥有对文档的管理权限。
     *
     * @param userId   用户 ID
     * @param document 知识库文档
     * @return 如果用户可管理该文档则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean canManage(Long userId, KbDocument document) {
        if (userId == null || document == null) {
            return false;
        }
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            return false;
        }
        if (!isAclEnabled()) {
            return userId.equals(document.getOwnerUserId());
        }
        if (userId.equals(document.getOwnerUserId())) {
            return true;
        }
        return hasPermission(document.getId(), userId, List.of(PERMISSION_MANAGE));
    }

    /**
     * 从给定的文档 ID 集合中筛选出当前用户有读取权限的文档 ID。
     *
     * @param userId      用户 ID
     * @param documentIds 文档 ID 集合
     * @return 用户有读取权限的文档 ID 集合，保持原始顺序
     */
    @Override
    public Set<Long> filterReadableDocumentIds(Long userId, Collection<Long> documentIds) {
        if (userId == null || documentIds == null || documentIds.isEmpty()) {
            return Set.of();
        }

        List<KbDocument> documents = kbDocumentRepository.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .in(KbDocument::getId, documentIds)
                        .eq(KbDocument::getDeleted, 0)
        );

        return documents.stream()
                .filter(document -> canRead(userId, document))
                .map(KbDocument::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 列出指定用户在指定知识库下有读取权限的所有文档 ID。
     *
     * @param userId 用户 ID
     * @param kbId   知识库 ID，若为 {@code null} 则查询所有知识库
     * @return 用户有读取权限的文档 ID 集合
     */
    @Override
    public Set<Long> listReadableDocumentIds(Long userId, Long kbId) {
        if (userId == null) {
            return Set.of();
        }

        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getDeleted, 0)
                .eq(KbDocument::getEnabled, 1);

        if (kbId != null) {
            wrapper.eq(KbDocument::getKbId, kbId);
        }

        List<KbDocument> documents = kbDocumentRepository.selectList(wrapper);
        return documents.stream()
                .filter(document -> canRead(userId, document))
                .map(KbDocument::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 判断指定用户是否对指定文档有分享权限。
     *
     * @param userId   用户 ID
     * @param document 文档
     * @return 如果用户有分享权限则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean canShare(Long userId, KbDocument document) {
        if (userId == null || document == null) {
            return false;
        }
        
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            return false;
        }
        
        if (!isAclEnabled()) {
            return userId.equals(document.getOwnerUserId());
        }
        if (userId.equals(document.getOwnerUserId())) {
            return true;
        }
        
        return hasPermission(document.getId(), userId, List.of(PERMISSION_SHARE, PERMISSION_MANAGE));
    }

    /**
     * 检查用户是否对指定文档拥有指定权限中的任意一项。
     *
     * @param documentId  文档 ID
     * @param userId      用户 ID
     * @param permissions 权限列表
     * @return 如果拥有任意一项权限则返回 {@code true}，否则返回 {@code false}
     */
    private boolean hasPermission(Long documentId, Long userId, List<String> permissions) {
        if (hasUserPermission(documentId, userId, permissions)) {
            return true;
        }

        Set<Long> groupIds = groupMembershipService.listGroupIdsByUserId(userId);
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }

        return hasGroupPermission(documentId, groupIds, permissions);
    }

    /**
     * 检查用户是否对指定文档拥有指定权限中的任意一项。
     *
     * @param documentId  文档 ID
     * @param userId      用户 ID
     * @param permissions 权限列表
     * @return 如果拥有任意一项权限则返回 {@code true}，否则返回 {@code false}
     */
    private boolean hasUserPermission(Long documentId, Long userId, List<String> permissions) {
        Long count = kbDocumentAclRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, PRINCIPAL_TYPE_USER)
                        .eq(KbDocumentAcl::getPrincipalId, String.valueOf(userId))
                        .in(KbDocumentAcl::getPermission, permissions)
        );
        return count != null && count > 0;
    }
  
    /**
     * 检查用户是否对指定文档拥有指定权限中的任意一项。
     *
     * @param documentId  文档 ID
     * @param groupIds    用户组 ID 列表
     * @param permissions 权限列表
     * @return 如果拥有任意一项权限则返回 {@code true}，否则返回 {@code false}
     */
    private boolean hasGroupPermission(Long documentId, Set<Long> groupIds, List<String> permissions) {
        Long count = kbDocumentAclRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, PRINCIPAL_TYPE_GROUP)
                        .in(KbDocumentAcl::getPrincipalId, groupIds.stream().map(String::valueOf).toList())
                        .in(KbDocumentAcl::getPermission, permissions)
        );
        return count != null && count > 0;
    }
}
