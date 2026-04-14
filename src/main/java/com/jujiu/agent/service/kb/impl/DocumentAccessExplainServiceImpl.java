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
 * 文档访问权限解释服务实现类。
 * <p>
 * 根据文档可见性、ACL 授权以及用户所属组等信息，
 * 逐条解释指定用户对某篇文档是否可见及其原因。
 * </p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:33
 */
@Service
public class DocumentAccessExplainServiceImpl implements DocumentAccessExplainService {

    /** 文档可见性：公开。 */
    private static final String VISIBILITY_PUBLIC = "PUBLIC";

    /** 文档可见性：组内共享。 */
    private static final String VISIBILITY_GROUP_SHARED = "GROUP_SHARED";

    /** ACL 主体类型：用户。 */
    private static final String PRINCIPAL_TYPE_USER = "USER";

    /** ACL 主体类型：用户组。 */
    private static final String PRINCIPAL_TYPE_GROUP = "GROUP";

    /** 权限类型：读。 */
    private static final String PERMISSION_READ = "READ";

    /** 权限类型：管理。 */
    private static final String PERMISSION_MANAGE = "MANAGE";

    /** 权限类型：共享。 */
    private static final String PERMISSION_SHARE = "SHARE";

    /** 文档持久化仓库。 */
    private final KbDocumentRepository kbDocumentRepository;

    /** 文档 ACL 持久化仓库。 */
    private final KbDocumentAclRepository kbDocumentAclRepository;

    /** 文档-用户组关联持久化仓库。 */
    private final KbDocumentGroupRepository kbDocumentGroupRepository;

    /** 组成员关系服务。 */
    private final GroupMembershipService groupMembershipService;

    /**
     * 构造方法。
     *
     * @param kbDocumentRepository      文档持久化仓库
     * @param kbDocumentAclRepository   文档 ACL 持久化仓库
     * @param kbDocumentGroupRepository 文档-用户组关联持久化仓库
     * @param groupMembershipService    组成员关系服务
     */
    public DocumentAccessExplainServiceImpl(KbDocumentRepository kbDocumentRepository,
                                            KbDocumentAclRepository kbDocumentAclRepository,
                                            KbDocumentGroupRepository kbDocumentGroupRepository,
                                            GroupMembershipService groupMembershipService) {
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentAclRepository = kbDocumentAclRepository;
        this.kbDocumentGroupRepository = kbDocumentGroupRepository;
        this.groupMembershipService = groupMembershipService;
    }
    /**
     * 解释指定用户对指定文档的访问权限。
     *
     * @param userId     用户 ID
     * @param documentId 文档 ID
     * @return 包含可见性、原因及详细说明的响应对象
     */
    @Override
    public KbDocumentAccessExplainResponse explainAccess(Long userId, Long documentId) {
        // 1. 查询文档基础信息
        KbDocument document = kbDocumentRepository.selectById(documentId);

        // 2. 校验文档是否存在
        if (document == null) {
            return build(documentId, userId, false, "DOCUMENT_NOT_FOUND", "文档不存在");
        }

        // 3. 校验文档是否已删除
        if (document.getDeleted() != null && document.getDeleted() == 1) {
            return build(documentId, userId, false, "DOCUMENT_DELETED", "文档已删除");
        }

        // 4. 校验文档是否已禁用
        if (document.getEnabled() != null && document.getEnabled() != 1) {
            return build(documentId, userId, false, "DOCUMENT_DISABLED", "文档已禁用");
        }

        // 5. 判断当前用户是否为文档所有者
        if (userId != null && userId.equals(document.getOwnerUserId())) {
            return build(documentId, userId, true, "OWNER", "当前用户是文档 owner");
        }

        // 6. 判断文档是否为公开可见
        if (VISIBILITY_PUBLIC.equalsIgnoreCase(document.getVisibility())) {
            return build(documentId, userId, true, "PUBLIC", "文档为 PUBLIC，所有登录用户可读");
        }

        // 7. 判断用户是否被直接授予读/管理/共享权限
        if (hasDirectUserReadLikePermission(documentId, userId)) {
            return build(documentId, userId, true, "DIRECT_USER_GRANT", "当前用户被直接授予文档访问权限");
        }

        // 8. 判断用户所属组是否被直接授予读/管理/共享权限
        if (hasDirectGroupReadLikePermission(documentId, userId)) {
            return build(documentId, userId, true, "DIRECT_GROUP_GRANT", "当前用户所属组被直接授予文档访问权限");
        }

        // 9. 判断文档为组共享且用户属于共享组成员
        if (VISIBILITY_GROUP_SHARED.equalsIgnoreCase(document.getVisibility())
                && isGroupSharedVisible(documentId, userId)) {
            return build(documentId, userId, true, "GROUP_SHARED_MEMBER", "当前用户属于文档共享组成员");
        }

        // 10. 未命中任何可见规则
        return build(documentId, userId, false, "NO_MATCHED_RULE", "当前用户未命中任何可见规则");
    }

    /**
     * 检查用户是否被直接授予读/管理/共享权限。
     *
     * @param documentId 文档 ID
     * @param userId     用户 ID
     * @return 若存在直接授权则返回 {@code true}
     */
    private boolean hasDirectUserReadLikePermission(Long documentId, Long userId) {
        // 1. 用户 ID 为空则直接返回 false
        if (userId == null) {
            return false;
        }

        // 2. 查询该用户对文档的 ACL 授权记录数
        Long count = kbDocumentAclRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, PRINCIPAL_TYPE_USER)
                        .eq(KbDocumentAcl::getPrincipalId, String.valueOf(userId))
                        .in(KbDocumentAcl::getPermission, List.of(PERMISSION_READ, PERMISSION_MANAGE, PERMISSION_SHARE))
        );

        // 3. 返回是否存在符合条件的授权
        return count != null && count > 0;
    }

    /**
     * 检查用户所属组是否被直接授予读/管理/共享权限。
     *
     * @param documentId 文档 ID
     * @param userId     用户 ID
     * @return 若用户所属任意组存在直接授权则返回 {@code true}
     */
    private boolean hasDirectGroupReadLikePermission(Long documentId, Long userId) {
        // 1. 用户 ID 为空则直接返回 false
        if (userId == null) {
            return false;
        }

        // 2. 获取用户所属的所有组 ID
        Set<Long> groupIds = groupMembershipService.listGroupIdsByUserId(userId);
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }

        // 3. 查询这些组对文档的 ACL 授权记录数
        Long count = kbDocumentAclRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, PRINCIPAL_TYPE_GROUP)
                        .in(KbDocumentAcl::getPrincipalId, groupIds.stream().map(String::valueOf).toList())
                        .in(KbDocumentAcl::getPermission, List.of(PERMISSION_READ, PERMISSION_MANAGE, PERMISSION_SHARE))
        );

        // 4. 返回是否存在符合条件的授权
        return count != null && count > 0;
    }

    /**
     * 判断用户是否属于文档的共享组，从而对 GROUP_SHARED 文档可见。
     *
     * @param documentId 文档 ID
     * @param userId     用户 ID
     * @return 若用户属于共享组则返回 {@code true}
     */
    private boolean isGroupSharedVisible(Long documentId, Long userId) {
        // 1. 用户 ID 为空则直接返回 false
        if (userId == null) {
            return false;
        }

        // 2. 获取用户所属的所有组 ID
        Set<Long> groupIds = groupMembershipService.listGroupIdsByUserId(userId);
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }

        // 3. 查询文档-用户组关联记录数
        Long count = kbDocumentGroupRepository.selectCount(
                new LambdaQueryWrapper<KbDocumentGroup>()
                        .eq(KbDocumentGroup::getDocumentId, documentId)
                        .in(KbDocumentGroup::getGroupId, groupIds)
        );

        // 4. 返回是否存在关联记录
        return count != null && count > 0;
    }

    /**
     * 构建权限解释响应对象。
     *
     * @param documentId 文档 ID
     * @param userId     用户 ID
     * @param visible    是否可见
     * @param reason     原因代码
     * @param detail     详细说明
     * @return 组装后的响应对象
     */
    private KbDocumentAccessExplainResponse build(Long documentId,
                                                  Long userId,
                                                  Boolean visible,
                                                  String reason,
                                                  String detail) {
        // 1. 使用 Builder 组装响应对象
        return KbDocumentAccessExplainResponse.builder()
                .documentId(documentId)
                .userId(userId)
                .visible(visible)
                .reason(reason)
                .detail(detail)
                .build();
    }
}
