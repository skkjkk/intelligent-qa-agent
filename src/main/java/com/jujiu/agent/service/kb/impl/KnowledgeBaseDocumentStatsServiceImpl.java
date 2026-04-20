package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.response.KbDimensionCountResponse;
import com.jujiu.agent.model.dto.response.KbDocumentStatsResponse;
import com.jujiu.agent.model.dto.response.KbTrendPointResponse;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.KnowledgeBaseDocumentStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * 查询文档统计信息
     * 
     * <p>查询并汇总知识库的文档统计数据，提供多维度的统计信息。
     * 包括文档数量、分块数量、文件类型分布、状态分布、趋势分析等。
     * 
     * <p>统计维度：
     * <ul>
     *     <li>文档数量统计：总数、成功数、处理中数、失败数</li>
     *     <li>文件类型统计：PDF、DOCX、MD、TXT、HTML</li>
     *     <li>分块统计：总Chunk数量</li>
     *     <li>分布统计：按文件类型分布、按状态分布</li>
     *     <li>趋势统计：7天趋势、30天趋势</li>
     * </ul>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>参数校验：验证userId有效性</li>
     *     <li>统计各状态文档数量（SUCCESS/PROCESSING/FAILED）</li>
     *     <li>统计各类型文档数量（PDF/DOCX/MD/TXT/HTML）</li>
     *     <li>汇总分块数量</li>
     *     <li>查询文件类型分布和状态分布</li>
     *     <li>查询7天和30天趋势数据</li>
     *     <li>构建并返回响应对象</li>
     * </ol>
     * 
     * <p>注意：
     * <ul>
     *     <li>该方法会执行多次数据库查询，注意性能</li>
     *     <li>kbId可选，为空时统计用户所有知识库</li>
     *     <li>已删除的文档不计入统计</li>
     * </ul>
     *
     * @param userId 当前用户 ID，必填
     * @param kbId 知识库 ID，可选，为空时统计用户所有知识库
     * @return 文档统计结果，包含多维度统计数据
     */
    @Override
    public KbDocumentStatsResponse getDocumentStats(Long userId, Long kbId, Integer windowDays, ZoneId zoneId, Integer topN) {
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

        // 5. 查询分布数据
        List<KbDimensionCountResponse> fileTypeDistribution = toDimension(kbDocumentRepository.aggregateByFileType(userId, kbId));
        List<KbDimensionCountResponse> statusDistribution = toDimension(kbDocumentRepository.aggregateByStatus(userId, kbId));

        // 6. 查询趋势数据
        LocalDateTime now = LocalDateTime.now();
        List<KbTrendPointResponse> trend7Days = toTrend(kbDocumentRepository.aggregateCreatedTrend(userId, kbId, now.minusDays(7)));
        List<KbTrendPointResponse> trend30Days = toTrend(kbDocumentRepository.aggregateCreatedTrend(userId, kbId, now.minusDays(30)));
        
        log.info("[KB][STATS] 文档统计查询完成 - userId={}, kbId={}, totalDocuments={}, totalChunks={}",
                userId, kbId, totalDocuments, totalChunks);

        // 7. 构建并返回响应对象
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
                .fileTypeDistribution(fileTypeDistribution)
                .statusDistribution(statusDistribution)
                .trend7Days(trend7Days)
                .trend30Days(trend30Days)
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

    /**
     * 将数据库查询结果转换为维度统计响应列表
     * 
     * <p>将SQL查询返回的Map列表转换为业务响应的DTO列表。
     * 这是一个数据转换方法，将数据库层面的数据格式转换为API响应格式。
     * 
     * <p>输入数据格式（来自SQL查询）：
     * <pre>
     * [
     *     {"dimName": "技术文档", "dimCount": 150},
     *     {"dimName": "产品说明", "dimCount": 80},
     *     {"dimName": "常见问题", "dimCount": 45}
     * ]
     * </pre>
     * 
     * <p>输出数据格式（业务响应DTO）：
     * <pre>
     * [
     *     KbDimensionCountResponse(name="技术文档", count=150),
     *     KbDimensionCountResponse(name="产品说明", count=80),
     *     KbDimensionCountResponse(name="常见问题", count=45)
     * ]
     * </pre>
     * 
     * <p>转换逻辑：
     * <ul>
     *     <li>dimName → name: 维度名称，直接转换</li>
     *     <li>dimCount → count: 维度计数值，使用longVal()安全转换</li>
     * </ul>
     * 
     * @param rows SQL查询返回的Map列表，每行包含dimName和dimCount
     * @return 维度统计响应DTO列表
     */
    private List<KbDimensionCountResponse> toDimension(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> KbDimensionCountResponse.builder()
                .name(String.valueOf(r.get("dimName")))
                .count(longVal(r.get("dimCount")))
                .build()).collect(Collectors.toList());
    }

    /**
     * 将数据库查询结果转换为趋势统计响应列表
     * 
     * <p>将SQL查询返回的Map列表转换为业务响应的DTO列表。
     * 这是一个数据转换方法，将数据库层面的数据格式转换为API响应格式。
     * 
     * <p>输入数据格式（来自SQL查询）：
     * <pre>
     * [
     *     {"dayVal": "2026-04-10", "dayCount": 100},
     *     {"dayVal": "2026-04-11", "dayCount": 120},
     *     {"dayVal": "2026-04-12", "dayCount": 150}
     * ]
     * </pre>
     * 
     * <p>输出数据格式（业务响应DTO）：
     * <pre>
     * [
     *     KbTrendPointResponse(day="2026-04-10", count=100),
     *     KbTrendPointResponse(day="2026-04-11", count=120),
     *     KbTrendPointResponse(day="2026-04-12", count=150)
     * ]
     * </pre>
     * 
     * <p>转换逻辑：
     * <ul>
     *     <li>dayVal → day: 日期值，直接转换</li>
     *     <li>dayCount → count: 日期计数值，使用longVal()安全转换</li>
     * </ul>
     * 
     * @param rows SQL查询返回的Map列表，每行包含dayVal和dayCount
     * @return 趋势统计响应DTO列表
     */
    private List<KbTrendPointResponse> toTrend(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> KbTrendPointResponse.builder()
                .day(String.valueOf(r.get("dayVal")))
                .count(longVal(r.get("dayCount")))
                .build()).collect(Collectors.toList());
    }

    /**
     * 安全转换对象为长整数。
     * 
     * @param v 待转换的对象
     * @return 转换后的长整数，0 表示转换失败
     */
    private Long longVal(Object v) {
        if (v == null) {
            return 0L;
        }
        
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }
}