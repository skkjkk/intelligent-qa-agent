package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.common.constant.BusinessConstants;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.deepseek.ToolCallDTO;
import com.jujiu.agent.model.dto.deepseek.ToolDefinition;
import com.jujiu.agent.service.FunctionCallingService;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Function Calling 服务实现类
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/25
 */
@Service
@Slf4j
public class FunctionCallingServiceImpl implements FunctionCallingService {
    
    @Autowired
    private ToolRegistry toolRegistry;
    
    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private ObjectMapper objectMapper;

    /** 最大迭代次数，防止无限循环 */
    private static final int MAX_ITERATIONS = 5;


    @Override
    public DeepSeekResult chatWithTools(List<DeepSeekMessage> messages) {
        log.info("[FUNCTION_CALLING] 开始带工具的对话 - messageCount={}", messages.size());
        
        // 1. 获取所有可用的工具定义
        List<ToolDefinition> toolDefinitions = getToolDefinitions();
        log.info("[FUNCTION_CALLING] 获取到工具数量 - count={}", toolDefinitions.size());
        
        // 2. 如果没有工具，直接调用普通对话
        if (toolDefinitions.isEmpty()) {
            log.warn("[FUNCTION_CALLING] 没有可用的工具，将进行普通对话");
            return deepSeekClient.chat(messages);
        }
        
        // 3. 循环调用，最多5次
        int iterations = 0;
        while (iterations < MAX_ITERATIONS) {
            iterations++;
            log.info("[FUNCTION_CALLING] 当前迭代次数 - iteration={}", iterations);
            
            // 4. 调用DeepSeek API
            DeepSeekResult result = deepSeekClient.chatWithTools(messages, toolDefinitions);
            
            // 5. 检查是否有工具调用
            List<ToolCallDTO> toolCalls = result.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("[FUNCTION_CALLING] AI不需要调用工具，返回最终结果");
                return result;
            }
            
            // 6. 将 assistant 的消息（包含 tool_calls）加入对话历史
            DeepSeekMessage assistantMessage = new DeepSeekMessage();
            assistantMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
            assistantMessage.setContent(result.getReply());
            assistantMessage.setToolCalls(toolCalls);
            messages.add(assistantMessage);

            // 7. 执行所有工具调用
            log.info("[FUNCTION_CALLING] AI需要调用{}个工具", result.getToolCalls().size());
            for (ToolCallDTO toolCall : result.getToolCalls()) {
                String toolResult = executeTool(toolCall);
                // 8. 将工具结果作为新消息加入对话
                messages.add(DeepSeekMessage.toolMessage(toolCall.getId(), toolResult));
            }
        }
        // 8. 超过最大次数，抛出异常
        log.error("[FUNCTION_CALLING] 工具调用超过最大次数 - maxIterations={}", MAX_ITERATIONS);
        throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
    }

    /**
     * 获取所有可用的工具定义
     * 从 ToolRegistry 获取工具并转换为 DeepSeek API 格式
     *
     * @return 工具定义列表
     */
    private List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> toolDefinitions = new ArrayList<>();
        
        // 1. 从ToolRegistry中获取所有工具
        List<AbstractTool> tools = toolRegistry.getAllTools();
        
        // 2. 转为ToolDefinition格式
        for (AbstractTool tool : tools) {
            ToolDefinition definition = convertToToolDefinition(tool);
            toolDefinitions.add(definition);
        }

        log.debug("[FUNCTION_CALLING] 工具定义转换完成 - count={}", toolDefinitions.size());
        return toolDefinitions;
    }

    /**
     * 将 AbstractTool 转换为 ToolDefinition
     *
     * @param tool 工具对象
     * @return 工具定义
     */
    private ToolDefinition convertToToolDefinition(AbstractTool tool) {
        ToolDefinition definition = new ToolDefinition();
        definition.setType("function");
        ToolDefinition.Function function = new ToolDefinition.Function();
        function.setName(tool.getName());
        function.setDescription(tool.getDescription());
        function.setParameters(tool.getParameters());

        definition.setFunction(function);

        log.debug("[FUNCTION_CALLING] 工具转换完成 - name={}", tool.getName());
        return definition;
    }

    /**
     * 执行工具调用
     *
     * @param toolCall 工具调用信息
     * @return 工具执行结果
     */
    private String executeTool(ToolCallDTO toolCall) {
        String toolName = toolCall.getFunction().getName();
        String arguments = toolCall.getFunction().getArguments();

        log.info("[FUNCTION_CALLING] 执行工具 - name={}, arguments={}", toolName, arguments);

        try {
            // 1. 从ToolRegistry中获取工具
            AbstractTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("[FUNCTION_CALLING] 工具不存在 - name={}", toolName);
                return "错误：工具不存在 - " + toolName;
            }

            if (arguments == null || arguments.trim().isEmpty()) {
                log.error("[FUNCTION_CALLING] 工具参数为空 - name={}", toolName);
                return "错误：工具参数为空";
            }
            // 2. 解析参数（JSON字符串->Map）
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(arguments, Map.class);

            // 3. 执行工具
            String result = tool.execute(params);
            
            log.info("[FUNCTION_CALLING] 工具执行成功 - name={}, resultLength={}", toolName, result.length());
            
            return result;
        } catch (Exception e) {
            
            log.error("[FUNCTION_CALLING] 工具执行失败 - name={}, error={}", toolName, e.getMessage(), e);
            return "错误：工具执行失败 - " + e.getMessage();
        }
    }
    
}

