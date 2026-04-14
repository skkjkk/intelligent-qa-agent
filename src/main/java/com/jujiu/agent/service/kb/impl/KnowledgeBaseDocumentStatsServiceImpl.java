package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.response.KbDocumentStatsResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.KnowledgeBaseDocumentStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库文档统计服务实现。
 *
 * <p>用于统计当前用户在指定知识库下的文档状态分布、
 * 文件类型分布以及分块数量。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Service
@Slf4j
public class KnowledgeBaseDocumentStatsServiceImpl implements KnowledgeBaseDocumentStatsService {
    /** 知识库文档仓储。 */
    private final KbDocumentRepository kbDocumentRepository;

    /**
     * 构造方法。
     *
     * @param kbDocumentRepository 知识库文档仓储
     */
    public KnowledgeBaseDocumentStatsServiceImpl(KbDocumentRepository kbDocumentRepository) {
        this.kbDocumentRepository = kbDocumentRepository;
    }

    /**
     * 查询文档统计信息。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID，可为空
     * @return 文档统计结果
     */
    @Override
    public KbDocumentStatsResponse getDocumentStats(Long userId, Long kbId) {
        // 1. 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }

        // 2. 统计各状态文档数量
        Long totalDocuments = countDocuments(userId, kbId, null, null);
        Long successDocuments = countDocuments(userId, kbId, "SUCCESS", null);
        Long processingDocuments = countDocuments(userId, kbId, "PROCESSING", null);
        Long failedDocuments = countDocuments(userId, kbId, "FAILED", null);

        // 3. 统计各类型文档数量
        Long pdfDocuments = countDocuments(userId, kbId, null, "pdf");
        Long docxDocuments = countDocuments(userId, kbId, null, "docx");
        Long mdDocuments = countDocuments(userId, kbId, null, "md");
        Long txtDocuments = countDocuments(userId, kbId, null, "txt");
        Long htmlDocuments = countDocuments(userId, kbId, null, "html");

        // 4. 汇总分块数量
        Long totalChunks = sumChunks(userId, kbId);

        log.info("[KB][STATS] 文档统计查询完成 - userId={}, kbId={}, totalDocuments={}, totalChunks={}",
                userId, kbId, totalDocuments, totalChunks);

        // 5. 构建并返回响应对象
        return KbDocumentStatsResponse.builder()
                .totalDocuments(totalDocuments)
                .successDocuments(successDocuments)
                .processingDocuments(processingDocuments)
                .failedDocuments(failedDocuments)
                .pdfDocuments(pdfDocuments)
                .docxDocuments(docxDocuments)
                .mdDocuments(mdDocuments)
                .txtDocuments(txtDocuments)
                .htmlDocuments(htmlDocuments)
                .totalChunks(totalChunks)
                .build();
    }

    /**
     * 统计文档数量。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param status 文档状态，可为空
     * @param fileType 文件类型，可为空
     * @return 文档数量
     */
    private Long countDocuments(Long userId, Long kbId, String status, String fileType) {
        // 1. 构建基础查询条件：属于当前用户且未删除
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getOwnerUserId, userId)
                .eq(KbDocument::getDeleted, 0);

        // 2. 按需追加知识库、状态、文件类型筛选条件
        if (kbId != null) {
            wrapper.eq(KbDocument::getKbId, kbId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KbDocument::getStatus, status);
        }
        if (fileType != null && !fileType.isBlank()) {
            wrapper.eq(KbDocument::getFileType, fileType);
        }

        // 3. 执行计数查询
        return kbDocumentRepository.selectCount(wrapper);
    }
    
    /**
     * 汇总分块数量。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @return 分块总数
     */
    private Long sumChunks(Long userId, Long kbId) {
        // 1. 构建基础查询条件
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getOwnerUserId, userId)
                .eq(KbDocument::getDeleted, 0);

        // 2. 若指定了知识库 ID，追加筛选条件
        if (kbId != null) {
            wrapper.eq(KbDocument::getKbId, kbId);
        }

        // 3. 查询文档列表并累加 chunkCount
        List<KbDocument> documents = kbDocumentRepository.selectList(wrapper);

        long total = 0L;
        for (KbDocument document : documents) {
            Integer chunkCount = document.getChunkCount();
            total += chunkCount == null ? 0 : chunkCount;
        }
        return total;
    }
    

}
