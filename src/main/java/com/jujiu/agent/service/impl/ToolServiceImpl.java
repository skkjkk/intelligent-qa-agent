package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.model.dto.deepseek.ToolDefinition;
import com.jujiu.agent.model.dto.request.ExecuteToolRequest;
import com.jujiu.agent.model.dto.response.ExecuteToolResponse;
import com.jujiu.agent.model.dto.response.ToolResponse;
import com.jujiu.agent.model.entity.Tool;
import com.jujiu.agent.service.ToolService;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     */
    private final ToolRegistry toolRegistry;
    
    /**
     * ObjectMapper 用于解析 JSON
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入
     */
    public ToolServiceImpl(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        
    }
    
    @Override
    public List<ToolResponse> getToolList() {
        List<ToolResponse> toolResponses = new ArrayList<>();
        for (Tool tool : toolRegistry.getEnabledTools()) {
            ToolResponse toolResponse = ToolResponse.builder()
                    .toolName(tool.getToolName())
                    // 直接用数据库的显示名称
                    .displayName(tool.getDisplayName())
                    .description(tool.getDescription())
                    // 传入 JSON 字符串
                    .parameters(convertToolParameters(tool.getParameters())) 
                    .build();
            toolResponses.add(toolResponse);
        }
        log.info("[工具列表] 返回 {} 个工具", toolResponses.size());
        return toolResponses;
    }

    @Override
    public ExecuteToolResponse executeTool(ExecuteToolRequest request) {
        long startTime = System.currentTimeMillis();
        
        String toolName = request.getToolName();
        Map<String, Object> parameters = request.getParameters();

        log.info("[工具执行] 开始执行工具：name={}, params={}", toolName, parameters);
        
        // 1. 从注册中心获取工具
        AbstractTool tool = toolRegistry.getImplementation(toolName);
        if (tool == null) {
            log.warn("[工具执行] 工具不存在：name={}", toolName);
            return ExecuteToolResponse.builder()
                    .toolName(toolName)
                    .success(false)
                    .errorMessage("工具不存在：" + toolName)
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
     */
    private String getDisplayName(String name) {
        return switch (name) {
            case "weather" -> "天气查询";
            case "calculator" -> "计算器";
            case "web_search" -> "网页搜索";
            case "translator" -> "翻译器";
            case "time" -> "时间日期";
            default -> "未知工具";
        };
    }

    /**
     * 解析工具描述中的参数信息
     * 【设计目的】
     * 从工具描述中提取参数定义
     *
     */
    private List<ToolResponse.ParameterDefinition> convertToolParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 解析 JSON 字符串
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = objectMapper.readValue(parametersJson, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) paramsMap.get("properties");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) paramsMap.getOrDefault("required", new ArrayList<>());

            if (properties == null) {
                return new ArrayList<>();
            }

            List<ToolResponse.ParameterDefinition> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                params.add(ToolResponse.ParameterDefinition.builder()
                        .name(entry.getKey())
                        .type((String) prop.get("type"))
                        .required(required.contains(entry.getKey()))
                        .description((String) prop.get("description"))
                        .build());
            }
            return params;
        } catch (Exception e) {
            log.error("[工具列表] 解析参数失败: json={}", parametersJson, e);
            return new ArrayList<>();
        }
    }

}
