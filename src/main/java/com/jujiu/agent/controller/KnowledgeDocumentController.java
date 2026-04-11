package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.UploadDocumentRequest;
import com.jujiu.agent.model.dto.response.DocumentProcessStatusResponse;
import com.jujiu.agent.model.dto.response.KbDocumentResponse;
import com.jujiu.agent.service.kb.DocumentService;
import com.jujiu.agent.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public KnowledgeDocumentController(DocumentService documentService) {
        this.documentService = documentService;
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
            @RequestParam(value = "kbId", required = false) Long kbId) {

        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setTitle(title);
        request.setVisibility(visibility);
        request.setKbId(kbId);

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
}
