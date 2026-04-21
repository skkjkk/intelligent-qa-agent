package com.jujiu.agent.module.kb.api.controller;

import com.jujiu.agent.shared.result.Result;
import com.jujiu.agent.module.kb.api.response.KbBatchOperationResponse;
import com.jujiu.agent.module.kb.api.response.KbIndexDiagnosisResponse;
import com.jujiu.agent.module.kb.application.service.DocumentService;
import com.jujiu.agent.shared.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11 20:33
 */
@RestController
@RequestMapping("/api/v1/kb/index")
@Tag(name = "知识库索引管理", description = "知识库索引触发与批量索引")
@Slf4j
public class KnowledgeIndexController {
    private final DocumentService documentService;

    public KnowledgeIndexController(DocumentService documentService) {
        this.documentService = documentService;
    }

    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    /**
     * 手动触发单文档索引。
     *
     * @param documentId 文档 ID
     * @return 执行结果
     */
    @PostMapping("/{documentId}")
    @Operation(summary = "手动触发文档索引", description = "将指定文档的分块写入 Elasticsearch")
    public Result<Void> indexDocument(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        documentService.indexDocument(userId, documentId);
        return Result.success(null, "文档索引任务执行成功");
    }

    /**
     * 批量索引当前用户的待处理文档。
     *
     * @return 执行结果
     */
    @PostMapping("/pending")
    @Operation(summary = "批量索引待处理文档", description = "批量索引当前用户已解析成功但尚未完成索引的文档")
    public Result<KbBatchOperationResponse> indexPendingDocuments() {
        Long userId = getCurrentUserId();
        KbBatchOperationResponse response = documentService.indexPendingDocuments(userId);
        return Result.success(response, response.getMessage());
    }

    /**
     * 重建单个文档索引。
     *
     * @param documentId 文档 ID
     * @return 执行结果
     */
    @PostMapping("/rebuild/{documentId}")
    @Operation(summary = "重建单文档索引", description = "删除指定文档原有索引数据并重新执行索引")
    public Result<Void> rebuildIndex(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        documentService.rebuildIndex(userId, documentId);
        return Result.success(null, "文档索引重建成功");
    }

    /**
     * 批量重建当前用户失败的文档索引。
     *
     * @return 执行结果
     */
    @PostMapping("/rebuild/failed")
    @Operation(summary = "批量重建失败索引", description = "批量重建当前用户索引失败的文档")
    public Result<KbBatchOperationResponse> rebuildFailedIndexes() {
        Long userId = getCurrentUserId();
        KbBatchOperationResponse response = documentService.rebuildFailedIndexes(userId);
        return Result.success(response, response.getMessage());
    }

    @GetMapping("/diagnose/{documentId}")
    @Operation(summary = "索引诊断", description = "诊断文档状态、分块与ES索引一致性")
    public Result<KbIndexDiagnosisResponse> diagnose(@PathVariable Long documentId) {
        Long userId = getCurrentUserId();
        return Result.success(documentService.diagnoseIndex(userId, documentId));
    }

    @PostMapping("/repair/inconsistent")
    @Operation(summary = "修复状态不一致数据", description = "批量修复解析/索引状态与实际数据不一致")
    public Result<KbBatchOperationResponse> repairInconsistent() {
        Long userId = getCurrentUserId();
        KbBatchOperationResponse response = documentService.repairInconsistentIndexState(userId);
        return Result.success(response, response.getMessage());
    }
}
