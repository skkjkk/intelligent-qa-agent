package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.common.constant.BusinessConstants;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.deepseek.ToolCallDTO;
import com.jujiu.agent.model.dto.deepseek.ToolDefinition;
import com.jujiu.agent.model.entity.Tool;
import com.jujiu.agent.service.FunctionCallingService;
import com.jujiu.agent.service.FunctionCallingService.StreamEvent;
import com.jujiu.agent.service.FunctionCallingService.StreamingChatResult;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
        while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
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
            // 如果内容为空字符串，设置为 null（DeepSeek API 要求）
            String content = result.getReply();
            assistantMessage.setContent(content != null && !content.isEmpty() ? content : null);
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
        log.error("[FUNCTION_CALLING] 工具调用超过最大次数 - maxIterations={}", BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
        throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
    }

    /**
     * 获取所有可用的工具定义
     * 从 ToolRegistry 获取工具并转换为 DeepSeek API 格式
     *
     * @return 工具定义列表
     */
    private List<ToolDefinition> getToolDefinitions() {
        // 1. 获取所有启用的工具
        List<Tool> enabledTools = toolRegistry.getEnabledTools();
        List<ToolDefinition> toolDefinitions = new ArrayList<>();
        
        for (Tool dbTool : enabledTools) {
            ToolDefinition definition = buildFromDatabase(dbTool);
            toolDefinitions.add(definition);
        }

        log.info("[FUNCTION_CALLING] 从数据库加载工具配置 - count={}", toolDefinitions.size());
        return toolDefinitions;
        
    }

    /**
     * 从数据库配置构建 ToolDefinition
     */
    private ToolDefinition buildFromDatabase(Tool dbTool) {
        // 1. 创建 ToolDefinition 对象
        ToolDefinition definition = new ToolDefinition();        
        // 1.1 设置工具类型
        definition.setType("function");

        // 2. 创建 Function 对象
        ToolDefinition.Function function = new ToolDefinition.Function();
        
        // 2.1 设置工具名称和描述
        function.setName(dbTool.getToolName());
        function.setDescription(dbTool.getDescription());

        // 3. 解析数据库的 JSON 参数
        if (dbTool.getParameters() != null) {
            try {
                Map<String, Object> params = objectMapper.readValue(
                        dbTool.getParameters(), new TypeReference<Map<String, Object>>() {}
                );
                // 3.1 设置参数
                function.setParameters(params);
            } catch (Exception e) {
                log.error("[FUNCTION_CALLING] 解析工具参数失败: toolName={}", dbTool.getToolName(), e);
                // fallback：使用代码里的参数（已经是 Map<String, Object>）
                AbstractTool impl = toolRegistry.getImplementation(dbTool.getToolName());
                if (impl != null) {
                    // 去掉强转
                    function.setParameters(impl.getParameters());
                }
            }
        }
        // 4. 设置 Function 对象
        definition.setFunction(function);
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
            AbstractTool tool = toolRegistry.getImplementation(toolName);
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

            log.error("[FUNCTION_CALLING] 工具执行失败 - name={}, error={}", toolName, e.getMessage());
            return "错误：工具执行失败 - " + e.getMessage();
        }
    }

    /**
     * 追加工具执行结果到消息列表
     *
     * @param messages  对话消息列表
     * @param toolCalls 工具调用列表
     * @return 追加结果后的消息列表
     */
    private List<DeepSeekMessage> appendToolResults(
            List<DeepSeekMessage> messages,
            List<ToolCallDTO> toolCalls
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return messages;
        }
        for (ToolCallDTO toolCall : toolCalls) {
            String result = executeTool(toolCall);
            messages.add(DeepSeekMessage.toolMessage(toolCall.getId(), result));
        }
        return messages;
    }

    @Override
    public StreamingChatResult streamChatWithTools(
            List<DeepSeekMessage> messages,
            Consumer<StreamEvent> eventConsumer) {
        
        log.info("[FUNCTION_CALLING][STREAM] 开始带工具的流式对话 - messageCount={}", messages.size());
        List<DeepSeekMessage> messagesToSave = new ArrayList<>();
        // 1. 获取所有可用的工具定义
        List<ToolDefinition> toolDefinitions = getToolDefinitions();
        if (toolDefinitions.isEmpty()) {
            log.warn("[FUNCTION_CALLING][STREAM] 没有可用工具，无法进行流式工具对话");
            throw new BusinessException(ResultCode.INTERNAL_ERROR);
        }

        // 2. 创建变量用于存储最终结果和统计信息
//        StringBuilder finalReply = new StringBuilder();
        
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        int totalTokens = 0;

        int iterations = 0;
        while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
            iterations++;
            log.info("[FUNCTION_CALLING][STREAM] 当前迭代次数 - iteration={}", iterations);

            // 3. 创建变量用于存储当前轮次的累积结果
            StreamingRoundAccumulator accumulator = new StreamingRoundAccumulator();
            
            // 3.1 获取流式响应
            Flux<DeepSeekClient.ToolStreamChunk> stream = deepSeekClient.chatStreamWithTools(messages, toolDefinitions);

            try {
                for (DeepSeekClient.ToolStreamChunk chunk : stream.toIterable()) {
                    // 3.2 处理流式响应 chunk
                    if (chunk == null) {
                        continue;
                    }

                    // 3.3 如果是结束 chunk，处理结束相关逻辑
                    if (chunk.isEnd()) {
                        if (chunk.getFinishReason() != null) {
                            accumulator.setFinishReason(chunk.getFinishReason());
                        }
                        if (chunk.getUsage() != null) {
                            accumulator.setUsage(chunk.getUsage());
                        }
                        continue;
                    }

                    // 3.4 如果有内容，追加到累积结果中
                    if (chunk.getContent() != null) {
                        accumulator.appendContent(chunk.getContent());
                        eventConsumer.accept(StreamEvent.message(chunk.getContent()));
//                        finalReply.append(chunk.getContent());
                    }

                    // 3.5 如果有工具调用，追加到累积结果中
                    if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                        accumulator.appendToolCalls(chunk.getToolCalls());
                    }

                    // 3.6 如果有结束原因，设置结束原因
                    if (chunk.getFinishReason() != null) {
                        accumulator.setFinishReason(chunk.getFinishReason());
                    }
                }
            } catch (Exception e) {
                log.error("[FUNCTION_CALLING][STREAM] 流式响应处理异常", e);
                throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
            }

            // 3.7 如果有 usage，累加统计信息
            if (accumulator.getUsage() != null) {
                DeepSeekClient.StreamResponse.StreamUsage usage = accumulator.getUsage();
                totalPromptTokens += usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                totalCompletionTokens += usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                totalTokens += usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
            }

            // 3.8 获取当前轮次的累积结果
            String roundContent = accumulator.getContent();
            
            // 3.9 获取当前轮次的工具调用列表
            List<ToolCallDTO> toolCalls = accumulator.buildToolCalls();

            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("[FUNCTION_CALLING][STREAM] AI不需要调用工具，返回最终结果");
                DeepSeekMessage finalMessage = new DeepSeekMessage();
                finalMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
                finalMessage.setContent(roundContent);
                messagesToSave.add(finalMessage);
                return new StreamingChatResult(
//                        finalReply.toString(),
                        roundContent,
                        totalPromptTokens,
                        totalCompletionTokens,
                        totalTokens,
                        messagesToSave
                );
            }

            log.info("[FUNCTION_CALLING][STREAM] AI需要调用{}个工具", toolCalls.size());

            // 4. 创建消息对象，将当前轮次的累积结果添加到消息列表中
            DeepSeekMessage assistantMessage = new DeepSeekMessage();
            assistantMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
            // 如果内容为空字符串，设置为 null（DeepSeek API 要求）
            assistantMessage.setContent(roundContent != null && !roundContent.isEmpty() ? roundContent : null);
            assistantMessage.setToolCalls(toolCalls);
            messages.add(assistantMessage);
            // 记录带 tool_calls 的消息
            messagesToSave.add(assistantMessage);
            
            // 4.1 遍历工具调用列表，执行工具调用并处理结果
            for (ToolCallDTO toolCall : toolCalls) {
                String toolName = toolCall.getFunction().getName();
                eventConsumer.accept(StreamEvent.toolStart(toolName));
                
                String result;
                boolean success;
                String errorMsg = null;
                try {
                    result = executeTool(toolCall);
                    success = true;
                } catch (Exception e) {
                    result = "错误：工具执行失败 - " + e.getMessage();
                    success = false;
                    errorMsg = e.getMessage();
                    log.error("[FUNCTION_CALLING][STREAM] 工具执行异常 - name={}", toolName, e);
                }
                
                eventConsumer.accept(StreamEvent.toolEnd(toolName, success, errorMsg));
                DeepSeekMessage toolMessage = DeepSeekMessage.toolMessage(toolCall.getId(), result);
                messages.add(toolMessage);
                // 记录tool消息到数据库
                messagesToSave.add(toolMessage);
            }
        }

        log.error("[FUNCTION_CALLING][STREAM] 工具调用超过最大次数 - maxIterations={}", BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
        throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
    }

    /**
     * 单轮流式聚合器
     * 把一轮流式分片拼成完整结果：完整文本、完整toolCalls、finishReason、usage
     */
    private static class StreamingRoundAccumulator {
        
        // 累计文本内容
        private final StringBuilder contentBuilder = new StringBuilder();
        
        // 累计工具调用
        private final Map<Integer, ToolCallBuffer> toolCallBuffers = new LinkedHashMap<>();

        // 轮次结束原因
        private String finishReason;
        
        // 轮次使用情况
        private DeepSeekClient.StreamResponse.StreamUsage usage;

        // 累积内容方法
        public void appendContent(String content) {
            if (content != null) {
                contentBuilder.append(content);
            }
        }

        // 累积工具调用方法
        public void appendToolCalls(List<DeepSeekClient.StreamResponse.StreamToolCallDelta> deltas) {
            if (deltas == null) {
                return;
            }
            for (DeepSeekClient.StreamResponse.StreamToolCallDelta delta : deltas) {
                if (delta == null || delta.getIndex() == null) {
                    continue;
                }
                // 根据 index 获取或创建工具调用缓冲区
                ToolCallBuffer buffer = toolCallBuffers.computeIfAbsent(delta.getIndex(), k -> new ToolCallBuffer());
                if (delta.getId() != null) {
                    buffer.id = delta.getId();
                }
                if (delta.getType() != null) {
                    buffer.type = delta.getType();
                }
                if (delta.getFunction() != null) {
                    DeepSeekClient.StreamResponse.StreamFunctionDelta function = delta.getFunction();
                    if (function.getName() != null) {
                        buffer.functionName = function.getName();
                    }
                    if (function.getArguments() != null) {
                        buffer.argumentsBuilder.append(function.getArguments());
                    }
                }
            }
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        public void setUsage(DeepSeekClient.StreamResponse.StreamUsage usage) {
            this.usage = usage;
        }

        public String getContent() {
            return contentBuilder.toString();
        }

        public List<ToolCallDTO> buildToolCalls() {
            List<ToolCallDTO> result = new ArrayList<>();
            for (Map.Entry<Integer, ToolCallBuffer> entry : toolCallBuffers.entrySet()) {
                ToolCallBuffer buffer = entry.getValue();
                if (buffer.id == null) {
                    continue;
                }
                ToolCallDTO dto = new ToolCallDTO();
                dto.setId(buffer.id);
                dto.setType(buffer.type != null ? buffer.type : "function");
                ToolCallDTO.Function function = new ToolCallDTO.Function();
                function.setName(buffer.functionName);
                function.setArguments(buffer.argumentsBuilder.toString());
                dto.setFunction(function);
                result.add(dto);
            }
            return result;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public DeepSeekClient.StreamResponse.StreamUsage getUsage() {
            return usage;
        }
    }

    /**
     * 工具调用缓冲区
     * 用于拼接流式分片的工具调用参数
     */
    private static class ToolCallBuffer {
        // 工具调用ID
        private String id;

        // 工具调用类型
        private String type;

        // 工具函数名称
        private String functionName;
        
        // 工具参数
        private final StringBuilder argumentsBuilder = new StringBuilder();
    }
    
    
}

