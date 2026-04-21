package com.jujiu.agent.module.kb.api.controller;

import com.jujiu.agent.module.kb.api.response.*;
import com.jujiu.agent.module.kb.application.service.*;
import com.jujiu.agent.shared.result.Result;
import com.jujiu.agent.module.kb.api.request.BindDocumentGroupRequest;
import com.jujiu.agent.module.kb.api.request.GrantDocumentAclRequest;
import com.jujiu.agent.module.kb.api.request.UploadDocumentRequest;
import com.jujiu.agent.shared.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档控制器。
 *
 * <p>负责知识库文档的上传、查询、删除与状态追踪等生命周期管理能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@RestController
@RequestMapping("/api/v1/kb/documents")
@Tag(name = "知识库文档管理", description = "文档上传、查询、删除与状态追踪")
@Slf4j
public class KnowledgeDocumentController {

    private final DocumentService documentService;
    private final DocumentAclManageService documentAclManageService;
    private final DocumentGroupService documentGroupService;
    private final DocumentAclAuditService documentAclAuditService;
    private final DocumentSharingQueryService documentSharingQueryService;
    private final DocumentAccessExplainService documentAccessExplainService;

    public KnowledgeDocumentController(DocumentService documentService,
                                       DocumentAclManageService documentAclManageService,
                                       DocumentGroupService documentGroupService,
                                       DocumentAclAuditService documentAclAuditService,
                                       DocumentSharingQueryService documentSharingQueryService,
                                       DocumentAccessExplainService documentAccessExplainService) {
        this.documentService = documentService;
        this.documentAclManageService = documentAclManageService;
        this.documentGroupService = documentGroupService;
        this.documentAclAuditService = documentAclAuditService;
        this.documentSharingQueryService = documentSharingQueryService;
        this.documentAccessExplainService = documentAccessExplainService;
        
    }

    /**
     * 从 Spring Security 上下文中获取当前登录用户的 ID。
     *
     * @return 当前用户 ID
     */
    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    /**
     * 上传文档。
     *
     * @param file 原始文件
     * @param title 文档标题
     * @param visibility 可见性
     * @param kbId 知识库 ID
     * @return 文档 ID
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传知识库文档并触发异步解析流水线")
    public Result<Long> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestParam(value = "groupId", required = false) Long groupId) {

        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setTitle(title);
        request.setVisibility(visibility);
        request.setKbId(kbId);
        request.setGroupId(groupId);

        Long userId = getCurrentUserId();
        Long documentId = documentService.uploadDocument(userId, file, request);
        return Result.success(documentId, "文档上传成功，正在处理中");
    }
    /**
     * 查询文档详情。
     *
     * @param documentId 文档 ID
     * @return 文档详情
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "查询文档详情")
    public Result<KbDocumentResponse> getDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        KbDocumentResponse response = documentService.getDocument(userId, documentId);
        return Result.success(response);
    }

    /**
     * 查询文档列表。
     *
     * @param kbId 知识库 ID，可为空
     * @return 文档列表
     */
    @GetMapping
    @Operation(summary = "查询文档列表")
    public Result<List<KbDocumentResponse>> listDocuments(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        return Result.success(documentService.listDocuments(userId, kbId));
    }

    /**
     * 查询文档处理状态。
     *
     * @param documentId 文档 ID
     * @return 文档处理状态
     */
    @GetMapping("/{documentId}/status")
    @Operation(summary = "查询文档处理状态")
    public Result<DocumentProcessStatusResponse> getDocumentStatus(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentService.getDocumentStatus(userId, documentId));
    }

    /**
     * 删除文档。
     *
     * @param documentId 文档 ID
     * @return 删除结果
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        documentService.deleteDocument(userId, documentId);
        return Result.success(null, "文档删除成功");
    }

    /**
     * 查询文档授权列表。
     *
     * @param documentId 文档 ID
     * @return 文档授权列表
     */
    @GetMapping("/{documentId}/acl")
    @Operation(summary = "查询文档授权列表")
    public Result<List<KbDocumentAclResponse>> listDocumentAcl(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentAclManageService.listDocumentAcl(userId, documentId));
    }

    /**
     * 给文档授权。
     *
     * @param documentId 文档 ID
     * @param request 授权请求
     * @return 授权结果
     */
    @PostMapping("/{documentId}/acl")
    @Operation(summary = "给文档授权")
    public Result<Void> grantDocumentAcl(@PathVariable Long documentId,
                                         @RequestBody @Valid GrantDocumentAclRequest request) {
        Long userId = getCurrentUserId();
        documentAclManageService.grantDocumentAcl(userId, documentId, request);
        return Result.success(null, "文档授权成功");
    }

    /**
     * 回收文档授权。
     *
     * @param documentId 文档 ID
     * @param principalType 授权主体类型
     * @param principalId 授权主体 ID
     * @param permission 授权权限
     * @return 授权回收结果
     */
    @DeleteMapping("/{documentId}/acl")
    @Operation(summary = "回收文档授权")
    public Result<Void> revokeDocumentAcl(@PathVariable Long documentId,
                                          @RequestParam("principalType") String principalType,
                                          @RequestParam("principalId") String principalId,
                                          @RequestParam("permission") String permission) {
        Long userId = getCurrentUserId();
        documentAclManageService.revokeDocumentAcl(userId, documentId, principalType, principalId, permission);
        return Result.success(null, "文档授权回收成功");
    }
    
    /**
     * 查询文档共享组。
     *
     * @param documentId 文档 ID
     * @return 文档共享组列表
     */
    @GetMapping("/{documentId}/groups")
    @Operation(summary = "查询文档共享组")
    public Result<List<KbDocumentGroupResponse>> listDocumentGroups(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentGroupService.listDocumentGroups(userId, documentId));
    }

    /**
     * 绑定文档共享组。
     *
     * @param documentId 文档 ID
     * @param request 绑定请求
     * @return 绑定结果
     */
    @PostMapping("/{documentId}/groups")
    @Operation(summary = "绑定文档共享组")
    public Result<Void> bindDocumentGroup(@PathVariable Long documentId,
                                          @RequestBody @Valid BindDocumentGroupRequest request) {
        Long userId = getCurrentUserId();
        documentGroupService.bindDocumentGroup(userId, documentId, request);
        return Result.success(null, "文档共享组绑定成功");
    }
    
    /**
     * 解绑文档共享组。
     *
     * @param documentId 文档 ID
     * @param groupId 组 ID
     * @return 解绑结果
     */
    @DeleteMapping("/{documentId}/groups/{groupId}")
    @Operation(summary = "解绑文档共享组")
    public Result<Void> unbindDocumentGroup(@PathVariable Long documentId,
                                            @PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        documentGroupService.unbindDocumentGroup(userId, documentId, groupId);
        return Result.success(null, "文档共享组解绑成功");
    }

    /**
     * 查询文档 ACL 审计日志。
     *
     * @param documentId 文档 ID
     * @param action 操作类型
     * @return ACL 审计日志列表
     */
    @GetMapping("/{documentId}/acl/audit")
    @Operation(summary = "查询文档 ACL 审计日志")
    public Result<List<KbDocumentAclAuditLogResponse>> listDocumentAclAuditLogs(
            @PathVariable Long documentId,
            @RequestParam(value = "action", required = false) String action) {
        Long userId = getCurrentUserId();
        return Result.success(documentAclAuditService.listAuditLogs(userId, documentId, action));
    }
    /**
     * 查询文档共享概览。
     *
     * @param documentId 文档 ID
     * @return 文档共享概览
     */
    @GetMapping("/{documentId}/sharing-overview")
    @Operation(summary = "查询文档共享概览")
    public Result<KbDocumentSharingOverviewResponse> getSharingOverview(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentSharingQueryService.getSharingOverview(userId, documentId));
    }

    /**
     * 解释当前用户对文档的可见性原因。
     *
     * @param documentId 文档 ID
     * @return 访问解释
     */
    @GetMapping("/{documentId}/access-explain")
    @Operation(summary = "解释当前用户对文档的可见性原因")
    public Result<KbDocumentAccessExplainResponse> explainAccess(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentAccessExplainService.explainAccess(userId, documentId));
    }

}
