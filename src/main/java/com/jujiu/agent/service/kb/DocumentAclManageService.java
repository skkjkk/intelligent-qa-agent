package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.request.GrantDocumentAclRequest;
import com.jujiu.agent.model.dto.response.KbDocumentAclResponse;

import java.util.List;

/**
 * 文档 ACL 管理服务接口。提供文档访问控制权限的查询、授予与撤销能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 15:17
 */
public interface DocumentAclManageService {
    /**
     * 查询指定文档的 ACL 列表。
     *
     * @param userId     当前用户 ID，用于权限校验
     * @param documentId 文档 ID
     * @return 文档 ACL 响应列表
     */
    List<KbDocumentAclResponse> listDocumentAcl(Long userId, Long documentId);

    /**
     * 为指定文档授予 ACL 权限。
     *
     * @param userId     当前用户 ID，用于权限校验
     * @param documentId 文档 ID
     * @param request    授予 ACL 的请求参数
     */
    void grantDocumentAcl(Long userId, Long documentId, GrantDocumentAclRequest request);

    /**
     * 撤销指定文档的 ACL 权限。
     *
     * @param userId        当前用户 ID，用于权限校验
     * @param documentId    文档 ID
     * @param principalType 主体类型，例如 USER、GROUP 等
     * @param principalId   主体标识，例如用户 ID
     * @param permission    权限类型，例如 READ、rebuildFailedIndexes 是做什么的 等
     */
    void revokeDocumentAcl(Long userId, Long documentId, String principalType, String principalId, String permission);
}
