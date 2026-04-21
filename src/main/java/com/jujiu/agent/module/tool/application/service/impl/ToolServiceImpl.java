package com.jujiu.agent.module.tool.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.tool.api.request.ExecuteToolRequest;
import com.jujiu.agent.module.tool.api.response.ExecuteToolResponse;
import com.jujiu.agent.module.tool.api.response.ToolExecutionResult;
import com.jujiu.agent.module.tool.api.response.ToolResponse;
import com.jujiu.agent.module.tool.domain.entity.Tool;
import com.jujiu.agent.module.tool.application.service.ToolService;
import com.jujiu.agent.module.tool.runtime.AbstractTool;
import com.jujiu.agent.module.tool.application.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具服务实现类。
 * <p>提供工具列表查询和工具执行能力，负责从 {@link ToolRegistry} 获取工具定义、
 * 解析工具参数，并代理调用具体的 {@link AbstractTool} 实现。</p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 16:53
 */
@Service
@Slf4j
public class ToolServiceImpl implements ToolService {

    /** 工具注册中心，用于获取已启用的工具及具体实现 */
    private final ToolRegistry toolRegistry;

    /** JSON 序列化/反序列化工具，用于解析工具参数定义 */
    private final ObjectMapper objectMapper;

    /**
     * 通过构造方法注入依赖。
     *
     * @param toolRegistry 工具注册中心
     * @param objectMapper JSON 序列化/反序列化工具
     */
    public ToolServiceImpl(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 获取当前已启用的工具列表。
     *
     * @return 工具响应列表，包含工具名称、显示名称、描述及参数定义
     */
    @Override
    public List<ToolResponse> getToolList() {
        // 1. 初始化返回列表
        List<ToolResponse> toolResponses = new ArrayList<>();

        // 2. 遍历注册中心中所有已启用的工具，构建响应对象
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

        // 3. 记录日志并返回结果
        log.info("[工具列表] 返回 {} 个工具", toolResponses.size());
        return toolResponses;
    }

    /**
     * 执行指定工具。
     *
     * @param request 工具执行请求，包含工具名称及调用参数
     * @return 工具执行响应，包含执行结果、耗时及错误信息
     */
    @Override
    public ExecuteToolResponse executeTool(ExecuteToolRequest request) {
        long startTime = System.currentTimeMillis();

        String toolName = request.getToolName();
        Map<String, Object> parameters = request.getParameters();

        log.info("[工具执行] 开始执行工具：name={}", toolName);

        // 1. 从注册中心获取工具实现
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
            // 2. 异步执行工具并设置 10 秒超时
            ToolExecutionResult result = CompletableFuture
                    .supplyAsync(() -> tool.executeStructured(parameters))
                    .orTimeout(10, TimeUnit.SECONDS)
                    .exceptionally(e -> {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        return ToolExecutionResult.builder()
                                .success(false)
                                .message("工具执行失败")
                                .errorCode(cause instanceof TimeoutException ? "TOOL_EXECUTE_TIMEOUT" : "TOOL_EXECUTE_ERROR")
                                .data(null)
                                .durationMs(System.currentTimeMillis() - startTime)
                                .build();
                    })
                    .join();
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("[工具执行] 执行成功：name={}, time={}ms", toolName, executionTime);

            // 3. 封装并返回成功结果
            return ExecuteToolResponse.builder()
                    .toolName(toolName)
                    .result(result.getData() == null ? null : result.getData().toString())
                    .executionTime(result.getDurationMs())
                    .success(result.isSuccess())
                    .errorMessage(result.isSuccess() ? null : result.getMessage())
                    .build();
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[工具执行] 执行失败：name={}, error={}", toolName, e.getMessage(), e);

            // 4. 封装并返回异常结果
            return ExecuteToolResponse.builder()
                    .toolName(toolName)
                    .executionTime(executionTime)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * 将工具参数 JSON 字符串转换为参数定义列表。
     *
     * @param parametersJson 工具参数 JSON 字符串，通常符合 JSON Schema 格式
     * @return 参数定义列表；解析失败或为空时返回空列表
     */
    private List<ToolResponse.ParameterDefinition> convertToolParameters(String parametersJson) {
        // 1. 空值校验
        if (parametersJson == null || parametersJson.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 2. 解析 JSON 字符串为 Map
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = objectMapper.readValue(parametersJson, Map.class);

            // 3. 提取 properties 和 required 字段
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) paramsMap.get("properties");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) paramsMap.getOrDefault("required", new ArrayList<>());

            // 4. 若不存在 properties，返回空列表
            if (properties == null) {
                return new ArrayList<>();
            }

            // 5. 遍历 properties，构建参数定义对象
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
            // 6. 解析异常时记录日志并返回空列表
            log.error("[工具列表] 解析参数失败", e);
            return new ArrayList<>();
        }
    }

}
