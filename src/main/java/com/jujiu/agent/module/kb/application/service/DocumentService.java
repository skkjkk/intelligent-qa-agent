package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.request.UploadDocumentRequest;
import com.jujiu.agent.module.kb.api.response.DocumentProcessStatusResponse;
import com.jujiu.agent.module.kb.api.response.KbBatchOperationResponse;
import com.jujiu.agent.module.kb.api.response.KbDocumentResponse;
import com.jujiu.agent.module.kb.api.response.KbIndexDiagnosisResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档服务。
 *
 * <p>负责知识库文档的生命周期管理，包括上传、查询、删除与状态追踪。
 *
 * @author 17644
 * @since 2026/4/1
 */
public interface DocumentService {

    /**
     * 上传文档。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>校验文件类型</li>
     *   <li>计算内容哈希</li>
     *   <li>文档去重检查</li>
     *   <li>保存原始文件到 MinIO</li>
     *   <li>写入 {@code kb_document}</li>
     *   <li>写入处理日志 {@code kb_document_process_log}</li>
     *   <li>发送 Kafka 文档处理事件</li>
     * </ol>
     *
     * @param userId  上传用户 ID
     * @param file    原始文件
     * @param request 上传请求元数据
     * @return 文档 ID
     */
    Long uploadDocument(Long userId, MultipartFile file, UploadDocumentRequest request);

    /**
     * 根据 ID 查询文档。
     *
     * @param documentId 文档 ID
     * @return 文档详情
     */
    KbDocumentResponse getDocument(Long userId, Long documentId);

    /**
     * 列出所有文档。
     *
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    List<KbDocumentResponse> listDocuments(Long userId, Long kbId);
    /**
     * 删除文档。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>从 {@code kb_document} 删除文档</li>
     *   <li>从 MinIO 删除原始文件</li>
     *   <li>写入处理日志 {@code kb_document_process_log}</li>
     *   <li>发送 Kafka 文档处理事件</li>
     * </ol>
     *
     * @param documentId 文档 ID
     */
    void deleteDocument(Long userId, Long documentId);

    /**
     * 获取文档处理状态。
     *
     * @param documentId 文档 ID
     * @return 处理状态
     */
    DocumentProcessStatusResponse getDocumentStatus(Long userId, Long documentId);
    
    /**
     * 索引所有待处理的文档。
     */
    KbBatchOperationResponse indexPendingDocuments(Long userId);
    
    /**
     * 索引文档。
     *
     * @param documentId 文档 ID
     */
    void indexDocument(Long userId, Long documentId);
    
    /**
     * 重建单个文档索引。
     *
     * @param userId 当前用户 ID
     * @param documentId 文档 ID
     */
    void rebuildIndex(Long userId, Long documentId);

    /**
     * 批量重建当前用户失败的文档索引。
     *
     * @param userId 当前用户 ID
     */
    KbBatchOperationResponse rebuildFailedIndexes(Long userId);

    /**
     * 诊断文档索引状态。
     *
     * @param userId 当前用户 ID
     * @param documentId 文档 ID
     * @return 索引诊断结果
     */
    KbIndexDiagnosisResponse diagnoseIndex(Long userId, Long documentId);

    /**
     * 修复文档索引不一致状态。
     *
     * @param userId 当前用户 ID
     * @return 修复操作结果
     */
    KbBatchOperationResponse repairInconsistentIndexState(Long userId);
}
