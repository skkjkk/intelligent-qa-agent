package com.jujiu.agent.module.kb.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.api.request.GrantDocumentAclRequest;
import com.jujiu.agent.module.kb.api.response.KbDocumentAclResponse;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.domain.entity.KbDocumentAcl;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentAclMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.application.service.DocumentAclAuditService;
import com.jujiu.agent.module.kb.application.service.DocumentAclManageService;
import com.jujiu.agent.module.kb.application.service.DocumentAclService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档 ACL 管理服务实现类。提供文档访问控制权限的查询、授予与撤销能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 15:23
 */
@Service
@Slf4j
public class DocumentAclManageServiceImpl implements DocumentAclManageService {
    /** 主体类型：用户。 */
    private static final String PRINCIPAL_TYPE_USER = "USER";
    /** 读取权限标识。 */
    private static final String PERMISSION_READ = "READ";
    /** 管理权限标识。 */
    private static final String PERMISSION_MANAGE = "rebuildFailedIndexes 是做什么的";
    /** 分享权限标识。 */
    private static final String PERMISSION_SHARE = "SHARE";
    /** 主体类型：用户组。 */
    private static final String PRINCIPAL_TYPE_GROUP = "GROUP";

    /** 知识库文档仓储。 */
    private final KbDocumentMapper kbDocumentMapper;
    /** 文档 ACL 仓储。 */
    private final KbDocumentAclMapper kbDocumentAclMapper;
    /** 文档 ACL 服务。 */
    private final DocumentAclService documentAclService;
    /** 文档 ACL 审计服务。 */
    private final DocumentAclAuditService documentAclAuditService;

    /**
     * 构造方法。
     *
     * @param kbDocumentMapper     知识库文档仓储
     * @param kbDocumentAclMapper  文档 ACL 仓储
     * @param documentAclService       文档 ACL 服务
     * @param documentAclAuditService  文档 ACL 审计服务
     */
    public DocumentAclManageServiceImpl(KbDocumentMapper kbDocumentMapper,
                                        KbDocumentAclMapper kbDocumentAclMapper,
                                        DocumentAclService documentAclService,
                                        DocumentAclAuditService documentAclAuditService) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.kbDocumentAclMapper = kbDocumentAclMapper;
        this.documentAclService = documentAclService;
        this.documentAclAuditService = documentAclAuditService;
    }

    /**
     * 查询指定文档的 ACL 列表。
     *
     * @param userId     当前用户 ID，用于权限校验
     * @param documentId 文档 ID
     * @return 文档 ACL 响应列表
     */
    @Override
    public List<KbDocumentAclResponse> listDocumentAcl(Long userId, Long documentId) {
        // 1. 校验当前用户是否具备该文档的管理权限
        KbDocument document = requireShareableDocument(userId, documentId);

        // 2. 查询该文档的所有 ACL 记录并按创建时间升序排列
        return kbDocumentAclMapper.selectList(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, document.getId())
                        .orderByAsc(KbDocumentAcl::getCreatedAt)
        ).stream()
                // 3. 将 ACL 实体转换为响应对象
                .map(acl -> KbDocumentAclResponse.builder()
                        .id(acl.getId())
                        .documentId(acl.getDocumentId())
                        .principalType(acl.getPrincipalType())
                        .principalId(acl.getPrincipalId())
                        .permission(acl.getPermission())
                        .createdAt(acl.getCreatedAt())
                        .build()
                ).toList();
    }

    /**
     * 为指定文档授予 ACL 权限。
     *
     * @param userId     当前用户 ID，用于权限校验
     * @param documentId 文档 ID
     * @param request    授予 ACL 的请求参数
     */
    @Override
    @Transactional
    public void grantDocumentAcl(Long userId,
                                 Long documentId,
                                 GrantDocumentAclRequest request) {
        // 1. 校验当前用户是否具备该文档的管理权限
        KbDocument document = requireShareableDocument(userId, documentId);

        // 2. 校验请求参数中的主体类型和权限类型是否合法
        validatePrincipalType(request.getPrincipalType());
        validatePermission(request.getPermission());

        String normalizedPrincipalType = request.getPrincipalType().toUpperCase();
        String normalizedPermission = request.getPermission().toUpperCase();


        // 3. 若授予对象为文档所有者本身，则无需重复授权，直接返回
        if (String.valueOf(document.getOwnerUserId()).equals(request.getPrincipalId())
                && PRINCIPAL_TYPE_USER.equalsIgnoreCase(request.getPrincipalType())) {
            return;
        }

        // 4. 查询是否已存在完全相同的 ACL 记录
        KbDocumentAcl existing = kbDocumentAclMapper.selectOne(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, normalizedPrincipalType)
                        .eq(KbDocumentAcl::getPrincipalId, request.getPrincipalId())
                        .eq(KbDocumentAcl::getPermission, normalizedPermission)
                        .last("LIMIT 1")
        );

        // 5. 若已存在相同权限记录，则直接返回，避免重复插入
        if (existing != null) {
            return;
        }

        // 6. 构建并保存新的 ACL 记录
        KbDocumentAcl acl = KbDocumentAcl.builder()
                .documentId(documentId)
                .principalType(request.getPrincipalType())
                .principalId(request.getPrincipalId())
                .permission(normalizedPermission)
                .createdAt(LocalDateTime.now())
                .build();
        
        kbDocumentAclMapper.insert(acl);

        // 7. 记录授权审计日志
        documentAclAuditService.logAclGrant(
                documentId,
                userId,
                normalizedPrincipalType,
                request.getPrincipalId(),
                normalizedPermission
        );
    }
    

    /**
     * 撤销指定文档的 ACL 权限。
     *
     * @param userId        当前用户 ID，用于权限校验
     * @param documentId    文档 ID
     * @param principalType 主体类型，例如 USER 等
     * @param principalId   主体标识，例如用户 ID
     * @param permission    权限类型，例如 READ 或 rebuildFailedIndexes 是做什么的
     */
    @Override
    @Transactional
    public void revokeDocumentAcl(Long userId,
                                  Long documentId,
                                  String principalType,
                                  String principalId,
                                  String permission) {
        // 1. 校验当前用户是否具备该文档的管理权限
        requireShareableDocument(userId, documentId);

        // 2. 校验请求参数中的主体类型和权限类型是否合法
        validatePrincipalType(principalType);
        validatePermission(permission);
        
        String normalizedPrincipalType = principalType.toUpperCase();
        String normalizedPermission = permission.toUpperCase();
        
        // 3. 删除匹配的 ACL 记录
        int deleted = kbDocumentAclMapper.delete(
                new LambdaQueryWrapper<KbDocumentAcl>()
                        .eq(KbDocumentAcl::getDocumentId, documentId)
                        .eq(KbDocumentAcl::getPrincipalType, normalizedPrincipalType)
                        .eq(KbDocumentAcl::getPrincipalId, principalId)
                        .eq(KbDocumentAcl::getPermission, normalizedPermission)
        );

        // 4. 记录撤销授权审计日志
        if (deleted > 0) {
            documentAclAuditService.logAclRevoke(
                    documentId,
                    userId,
                    normalizedPrincipalType,
                    principalId,
                    normalizedPermission
            );
        }
    }

    /**
     * 获取文档并校验当前用户是否具备管理权限。
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @return 知识库文档实体
     * @throws BusinessException 文档不存在或无管理权限时抛出
     */
    private KbDocument requireManageableDocument(Long userId, Long documentId) {
        // 根据文档 ID 查询文档信息
        KbDocument document = kbDocumentMapper.selectById(documentId);
        // 校验文档是否存在且未被删除
        if (document == null || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        // 校验当前用户是否具备管理权限
        if (!documentAclService.canManage(userId, document)) {
            documentAclAuditService.logAccessDenied(documentId, userId, "NO_MANAGE_PERMISSION");
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        return document;
    }

    /**
     * 校验主体类型是否合法。
     *
     * @param principalType 主体类型
     * @throws BusinessException 主体类型为空或不支持时抛出
     */
    private void validatePrincipalType(String principalType) {
        // 校验主体类型是否为空
        if (principalType == null || principalType.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "principalType 不能为空");
        }
        // 校验主体类型是否受支持
        if (!PRINCIPAL_TYPE_USER.equalsIgnoreCase(principalType)
                && !PRINCIPAL_TYPE_GROUP.equalsIgnoreCase(principalType)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "当前仅支持 USER 或 GROUP 主体类型");
        }
    }

    /**
     * 校验权限类型是否合法。
     *
     * @param permission 权限类型
     * @throws BusinessException 权限类型为空或不支持时抛出
     */
    private void validatePermission(String permission) {
        // 校验权限类型是否为空
        if (permission == null || permission.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "permission 不能为空");
        }
        // 校验权限类型是否受支持
        if (!PERMISSION_READ.equalsIgnoreCase(permission)
                && !PERMISSION_MANAGE.equalsIgnoreCase(permission)
                && !PERMISSION_SHARE.equalsIgnoreCase(permission)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "当前仅支持 READ、rebuildFailedIndexes 是做什么的 或 SHARE 权限");
        }
    }
    
    /**
     * 获取文档并校验当前用户是否具备分享权限。
     *
     * @param userId     当前用户 ID
     * @param documentId 文档 ID
     * @return 知识库文档实体
     * @throws BusinessException 文档不存在或无分享权限时抛出
     */
    private KbDocument requireShareableDocument(Long userId, Long documentId) {
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (!documentAclService.canShare(userId, document)) {
            documentAclAuditService.logAccessDenied(documentId, userId, "NO_SHARE_PERMISSION");
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        return document;
    }

}