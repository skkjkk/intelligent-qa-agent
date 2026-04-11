package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.QueryFeedbackRequest;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.KbQueryHistoryResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.service.kb.QueryLogService;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 知识库问答控制器。
 *
 * <p>负责知识库问答主链路相关接口，包括同步问答、流式问答、
 * 历史查询与反馈提交。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@RestController
@RequestMapping("/api/v1/kb/query")
@Tag(name = "知识库问答管理", description = "知识库问答、流式问答、历史与反馈")
@Slf4j
public class KnowledgeQueryController {
    private final RagService ragService;
    private final QueryLogService queryLogService;

    public KnowledgeQueryController(RagService ragService,
                                    QueryLogService queryLogService) {
        this.ragService = ragService;
        this.queryLogService = queryLogService;
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
     * 知识库同步问答。
     *
     * @param request 问答请求
     * @return 问答结果
     */
    @PostMapping
    @Operation(summary = "知识库问答", description = "基于知识库文档进行检索问答并返回引用信息")
    public Result<KnowledgeQueryResponse> query(@RequestBody @Valid QueryKnowledgeBaseRequest request) {
        Long userId = getCurrentUserId();
        KnowledgeQueryResponse response = ragService.query(userId, request);
        return Result.success(response);
    }

    /**
     * 知识库流式问答。
     *
     * @param request 问答请求
     * @return SSE 发射器
     */
    @PostMapping("/stream")
    @Operation(summary = "知识库流式问答", description = "基于知识库文档进行流式检索问答")
    public SseEmitter queryStream(@RequestBody @Valid QueryKnowledgeBaseRequest request) {
        Long userId = getCurrentUserId();
        return ragService.queryStream(userId, request);
    }
    
    /**
     * 查询当前用户的知识库问答历史。
     *
     * @param kbId 知识库 ID，可为空
     * @return 历史记录列表
     */
    @GetMapping("/history")
    @Operation(summary = "查询知识库问答历史", description = "查询当前用户的知识库问答历史记录")
    public Result<List<KbQueryHistoryResponse>> queryHistory(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        return Result.success(queryLogService.listQueryHistory(userId, kbId));
    }


    /**
     * 提交知识库问答反馈。
     *
     * @param queryLogId 查询日志 ID
     * @param request 反馈请求
     * @return 提交结果
     */
    @PostMapping("/{queryLogId}/feedback")
    @Operation(summary = "提交知识库问答反馈", description = "提交当前用户对知识库问答结果的反馈")
    public Result<Void> submitFeedback(@PathVariable Long queryLogId,
                                       @RequestBody @Valid QueryFeedbackRequest request) {
        Long userId = getCurrentUserId();
        queryLogService.saveFeedback(userId, queryLogId, request);
        return Result.success(null, "反馈提交成功");
    }
}


