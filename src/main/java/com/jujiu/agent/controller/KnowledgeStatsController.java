package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.response.KbDocumentStatsResponse;
import com.jujiu.agent.model.dto.response.KbHealthResponse;
import com.jujiu.agent.model.dto.response.KbQueryStatsResponse;
import com.jujiu.agent.model.dto.response.KbStatsOverviewResponse;
import com.jujiu.agent.service.kb.KnowledgeBaseDocumentStatsService;
import com.jujiu.agent.service.kb.KnowledgeBaseHealthService;
import com.jujiu.agent.service.kb.KnowledgeBaseQueryStatsService;
import com.jujiu.agent.service.kb.KnowledgeBaseStatsService;
import com.jujiu.agent.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库统计与健康检查控制器。
 *
 * <p>负责知识库概览统计与健康检查相关接口。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@RestController
@RequestMapping("/api/v1/kb")
@Tag(name = "知识库统计与健康", description = "知识库统计与健康检查")
@Slf4j
public class KnowledgeStatsController {
    private final KnowledgeBaseStatsService knowledgeBaseStatsService;
    private final KnowledgeBaseHealthService knowledgeBaseHealthService;
    private final KnowledgeBaseDocumentStatsService knowledgeBaseDocumentStatsService;
    private final KnowledgeBaseQueryStatsService knowledgeBaseQueryStatsService;

    public KnowledgeStatsController(KnowledgeBaseStatsService knowledgeBaseStatsService,
                                    KnowledgeBaseHealthService knowledgeBaseHealthService,
                                    KnowledgeBaseDocumentStatsService knowledgeBaseDocumentStatsService,
                                    KnowledgeBaseQueryStatsService knowledgeBaseQueryStatsService) {
        this.knowledgeBaseStatsService = knowledgeBaseStatsService;
        this.knowledgeBaseHealthService = knowledgeBaseHealthService;
        this.knowledgeBaseDocumentStatsService = knowledgeBaseDocumentStatsService;
        this.knowledgeBaseQueryStatsService = knowledgeBaseQueryStatsService;
    }

    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    /**
     * 查询知识库概览统计。
     *
     * @param kbId 知识库 ID，可为空
     * @return 概览统计信息
     */
    @GetMapping("/stats/overview")
    @Operation(summary = "查询知识库概览统计", description = "查询当前用户在指定知识库下的文档、查询与反馈概览统计")
    public Result<KbStatsOverviewResponse> getOverview(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        return Result.success(knowledgeBaseStatsService.getOverview(userId, kbId));
    }

    /**
     * 执行知识库健康检查。
     *
     * @return 健康检查结果
     */
    @GetMapping("/health")
    @Operation(summary = "知识库健康检查", description = "检查知识库相关依赖与配置状态")
    public Result<KbHealthResponse> health() {
        return Result.success(knowledgeBaseHealthService.checkHealth());
    }

    @GetMapping("/stats/documents")
    @Operation(summary = "查询知识库文档统计", description = "查询当前用户在指定知识库下的文档状态、类型与分块统计")
    public Result<KbDocumentStatsResponse> getDocumentStats(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        return Result.success(knowledgeBaseDocumentStatsService.getDocumentStats(userId, kbId));
    }
    @GetMapping("/stats/queries")
    @Operation(summary = "查询知识库问答统计", description = "查询当前用户在指定知识库下的问答数量、状态与平均耗时统计")
    public Result<KbQueryStatsResponse> getQueryStats(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        return Result.success(knowledgeBaseQueryStatsService.getQueryStats(userId, kbId));
    }
}
