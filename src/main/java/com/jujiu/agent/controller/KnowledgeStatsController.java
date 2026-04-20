package com.jujiu.agent.controller;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.common.result.ResultCode;
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
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
     * 执行知识库健康检查。
     *
     * @return 健康检查结果
     */
    @GetMapping("/health")
    @Operation(summary = "知识库健康检查", description = "检查知识库相关依赖与配置状态")
    public Result<KbHealthResponse> health() {
        return Result.success(knowledgeBaseHealthService.checkHealth());
    }
    
    /**
     * 查询知识库概览统计。
     *
     * @param kbId 知识库 ID，可为空
     * @return 概览统计信息
     */
    @GetMapping("/stats/overview")
    @Operation(summary = "查询知识库概览统计", description = "查询当前用户在指定知识库下的文档、查询与反馈概览统计")
    public ResponseEntity<Result<KbStatsOverviewResponse>> getOverview(
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestParam(value = "windowDays", required = false, defaultValue = "30") Integer windowDays,
            @RequestParam(value = "tz", required = false, defaultValue = "Asia/Shanghai") String tz,
            @RequestParam(value = "topN", required = false, defaultValue = "10") Integer topN) {

        int normalizedWindowDays = normalizeWindowDays(windowDays);
        int normalizedTopN = normalizeTopN(topN);
        ZoneId zoneId = normalizeZoneId(tz);
        Long userId = getCurrentUserId();

        KbStatsOverviewResponse data = knowledgeBaseStatsService.getOverview(
                userId, kbId, normalizedWindowDays, zoneId, normalizedTopN
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(Result.success(data));
    }

    @GetMapping("/stats/documents")
    @Operation(summary = "查询知识库文档统计", description = "查询当前用户在指定知识库下的文档状态、类型与分块统计")
    public ResponseEntity<Result<KbDocumentStatsResponse>> getDocumentStats(
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestParam(value = "windowDays", required = false, defaultValue = "30") Integer windowDays,
            @RequestParam(value = "tz", required = false, defaultValue = "Asia/Shanghai") String tz,
            @RequestParam(value = "topN", required = false, defaultValue = "10") Integer topN) {

        int normalizedWindowDays = normalizeWindowDays(windowDays);
        int normalizedTopN = normalizeTopN(topN);
        ZoneId zoneId = normalizeZoneId(tz);
        Long userId = getCurrentUserId();

        KbDocumentStatsResponse data = knowledgeBaseDocumentStatsService.getDocumentStats(
                userId, kbId, normalizedWindowDays, zoneId, normalizedTopN
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(Result.success(data));
    }

    @GetMapping("/stats/queries")
    @Operation(summary = "查询知识库问答统计", description = "查询当前用户在指定知识库下的问答数量、状态与平均耗时统计")
    public ResponseEntity<Result<KbQueryStatsResponse>> getQueryStats(
            @RequestParam(value = "kbId", required = false) Long kbId,
            @RequestParam(value = "windowDays", required = false, defaultValue = "30") Integer windowDays,
            @RequestParam(value = "tz", required = false, defaultValue = "Asia/Shanghai") String tz,
            @RequestParam(value = "topN", required = false, defaultValue = "10") Integer topN) {

        int normalizedWindowDays = normalizeWindowDays(windowDays);
        int normalizedTopN = normalizeTopN(topN);
        ZoneId zoneId = normalizeZoneId(tz);
        Long userId = getCurrentUserId();

        KbQueryStatsResponse data = knowledgeBaseQueryStatsService.getQueryStats(
                userId, kbId, normalizedWindowDays, zoneId, normalizedTopN
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(Result.success(data));
    }

    private int normalizeWindowDays(Integer windowDays) {
        Set<Integer> allowed = Set.of(7, 30, 90);
        if (windowDays == null || !allowed.contains(windowDays)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "windowDays 仅允许 7/30/90");
        }
        return windowDays;
    }

    private int normalizeTopN(Integer topN) {
        if (topN == null || topN < 1 || topN > 50) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "topN 仅允许 1~50");
        }
        return topN;
    }

    private ZoneId normalizeZoneId(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "tz 非法，必须是 IANA 时区");
        }
    }
}
