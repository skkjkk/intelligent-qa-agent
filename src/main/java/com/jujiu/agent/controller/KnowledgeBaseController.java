package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.request.UploadDocumentRequest;
import com.jujiu.agent.model.dto.response.DocumentProcessStatusResponse;
import com.jujiu.agent.model.dto.response.KbDocumentResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.service.kb.DocumentService;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 17:15
 */
@RestController
@RequestMapping("/api/v1/kb/documents")
@Tag(name = "知识库文档管理", description = "文档上传、查询与状态追踪")
@Slf4j
public class KnowledgeBaseController {


    private final DocumentService documentService;
    private final RagService ragService;

    public KnowledgeBaseController(DocumentService documentService,
                                   RagService ragService) {
        this.documentService = documentService;
        this.ragService = ragService;
    }
    
    /**
     * 从 Spring Security 上下文中获取当前登录用户的 ID
     *
     * @return 当前用户 ID，如果未认证则可能抛出异常
     */
    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }
    
    /**
     * 上传文档。
     *
     * @param file    原始文件
     * @param kbId 文档元数据
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

    @GetMapping
    @Operation(summary = "查询文档列表")
    public Result<List<KbDocumentResponse>> listDocuments(@RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        return Result.success(documentService.listDocuments(userId, kbId));
    }

    @GetMapping("/{documentId}/status")
    @Operation(summary = "查询文档处理状态")
    public Result<DocumentProcessStatusResponse> getDocumentStatus(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentService.getDocumentStatus(userId, documentId));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        documentService.deleteDocument(userId, documentId);
        return Result.success(null, "文档删除成功");
    }
    
    
    @PostMapping("/query")
    @Operation(summary = "知识库问答", description = "基于知识库文档进行检索问答并返回引用信息")
    public Result<KnowledgeQueryResponse> query(@RequestBody @Valid QueryKnowledgeBaseRequest request) {
        Long userId = getCurrentUserId();
        KnowledgeQueryResponse response = ragService.query(userId, request);
        return Result.success(response);
    }

    @PostMapping("/{documentId}/index")
    @Operation(summary = "手动触发文档索引", description = "将指定文档的分块写入 Elasticsearch")
    public Result<Void> indexDocument(@PathVariable Long documentId) {
        documentService.indexDocument(documentId);
        return Result.success(null, "文档索引任务执行成功");
    }

    @PostMapping("/index/pending")
    @Operation(summary = "批量索引待处理文档", description = "批量索引所有已解析成功但尚未完成索引的文档")
    public Result<Void> indexPendingDocuments() {
        documentService.indexPendingDocuments();
        return Result.success(null, "待处理文档索引任务执行成功");
    }
}
