package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.model.dto.request.ExecuteToolRequest;
import com.jujiu.agent.model.dto.response.ExecuteToolResponse;
import com.jujiu.agent.model.dto.response.ToolResponse;
import com.jujiu.agent.service.ToolService;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 16:53
 */
@Service
@Slf4j
public class ToolServiceImpl implements ToolService {
    /**
     * 工具注册中心
     *
     * 【为什么注入 ToolRegistry？】
     * 需要从注册中心获取工具列表和具体工具
     */
    private final ToolRegistry toolRegistry;
    
    /**
     * ObjectMapper 用于解析 JSON
     *
     * 【为什么需要？】
     * 工具的 parameters 字段是 JSON 字符串，
     * 需要解析成 Map<String, Object> 传给工具执行
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入
     *
     * 【为什么用构造器注入？】
     * - 依赖关系一目了然
     * - 便于单元测试
     * - 这是推荐的依赖注入方式
     */
    public ToolServiceImpl(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        
    }
    
    @Override
    public List<ToolResponse> getToolList() {
        List<ToolResponse> toolResponses = new ArrayList<>();
        for (AbstractTool tool : toolRegistry.getAllTools()) {
            ToolResponse toolResponse = ToolResponse.builder()
                    .toolName(getDisplayName(tool.getName()))
                    .description(tool.getDescription())
                    .parameters(parseParameters(tool.getDescription()))
                    .build();
            toolResponses.add(toolResponse);
        }
        log.info("[工具列表] 返回 {} 个工具, {}", toolResponses.size(), toolResponses.toString());
        return toolResponses;
    }

    @Override
    public ExecuteToolResponse executeTool(ExecuteToolRequest request) {
        long startTime = System.currentTimeMillis();
        
        String toolName = request.getToolName();
        Map<String, Object> parameters = request.getParameters();

        log.info("[工具执行] 开始执行工具：name={}, params={}", toolName, parameters);
        
        // 1. 从注册中心获取工具
        AbstractTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.warn("[工具执行] 工具不存在：name={}", toolName);
            return ExecuteToolResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("工具不存在" + toolName)
                    .build();
        }

        try {
            // 2. 执行工具
            String result = tool.execute(parameters);
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("[工具执行] 执行成功：name={}, time={}ms, result={}", toolName, executionTime, result);

            // 3. 返回结果
            return ExecuteToolResponse.builder()
                    .toolName(toolName)
                    .result(result)
                    .executionTime(executionTime)
                    .success(true)
                    .build();
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[工具执行] 执行失败：name={}, error={}", toolName, e.getMessage(), e);
            
            // 4. 返回错误结果
            return ExecuteToolResponse.builder()
                    .toolName(toolName)
                    .executionTime(executionTime)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * 获取工具显示名称
     *
     * 【设计目的】
     * 将工具名称转换为友好的显示名称
     * 如 "weather" → "天气查询"
     *
     * 【为什么不直接用 name？】
     * name 是给系统用的（唯一标识），
     * displayName 是给用户看的（友好名称）
     */
    private String getDisplayName(String name) {
        return switch (name) {
            case "weather" -> "天气查询";
            case "calculator" -> "计算器";
            case "search" -> "搜索";
            default -> "未知工具";
        };
    }

    /**
     * 解析工具描述中的参数信息
     *
     * 【设计目的】
     * 从工具描述中提取参数定义
     * 这里简化处理，实际可以从数据库 tool 表的 parameters 字段读取
     *
     * 【为什么简化处理？】
     * - 保持简单，先跑通功能
     * - 后续可以从数据库读取更规范的参数定义
     */
    private List<ToolResponse.ParameterDefinition> parseParameters(String description) {
        // 简化处理：返回一个通用参数定义
        // TODO: 实际项目中应该从数据库或工具类的 getParameters() 方法获取
        List<ToolResponse.ParameterDefinition> params = new ArrayList<>();

        // 如果描述中包含 city 参数
        if (description != null && description.contains("city")) {
            params.add(ToolResponse.ParameterDefinition.builder()
                    .name("city")
                    .type("string")
                    .required(true)
                    .description("城市名称")
                    .build());
        }

        return params;
    }
}
