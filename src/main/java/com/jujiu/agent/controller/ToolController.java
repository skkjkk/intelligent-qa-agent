package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.ExecuteToolRequest;
import com.jujiu.agent.model.dto.response.ExecuteToolResponse;
import com.jujiu.agent.model.dto.response.ToolResponse;
import com.jujiu.agent.service.ToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 17:08
 */
@RestController
@RequestMapping("/api/v1/tools")
@Tag(name = "工具管理", description = "获取可用工具列表、执行工具调用")
@Slf4j
public class ToolController {
    /**
     * 工具服务
     *
     * 【为什么注入接口而不是实现类？】
     * - 符合依赖倒置原则
     * - 便于测试时替换为 Mock 实现
     * - 实现类可以更换而不影响 Controller
     */
    @Autowired
    private ToolService toolService;

    /**
     * 获取所有可用工具列表
     *
     * 【设计目的】
     * 返回系统中所有已注册的工具，供前端展示
     *
     * 【为什么要公开这个接口？】
     * - 前端需要展示可用工具列表
     * - 用户可以了解系统能做什么
     *
     * 应该添加 @PreAuthorize 保护获取工具列表接口
     * 因为执行工具可能消耗资源或调用付费 API
     */
    @Operation(summary = "获取工具列表", description = "获取所有可用的工具列表")
    @GetMapping("/list")
    public Result<List<ToolResponse>> getToolList() {
        log.info("[工具列表] 收到获取工具列表请求");
        List<ToolResponse> toolList = toolService.getToolList();
        return Result.success(toolList);
    }

    @Operation(summary = "执行工具", description = "根据工具名称和参数执行工具")
    @PostMapping("/execute")
    @PreAuthorize(value = "hasRole('USER')")  // 需要登录
    public Result<ExecuteToolResponse> executeTool(@RequestBody @Valid ExecuteToolRequest request) {
        log.info("[工具执行] 收到执行工具请求");

        ExecuteToolResponse response = toolService.executeTool(request);
        
        // 返回执行结果
        // 注意：这里不判断 success，直接返回，让前端决定如何处理
        return Result.success(response);
    }
}
