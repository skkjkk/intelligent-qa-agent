package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
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

import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolExecutionContext;
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
    private static final int STREAM_MESSAGE_FLUSH_MIN_LENGTH = 32;

    private final ToolRegistry toolRegistry;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public FunctionCallingServiceImpl(ToolRegistry toolRegistry, DeepSeekClient deepSeekClient, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeepSeekResult chatWithTools(Long userId,List<DeepSeekMessage> messages) {
        ToolExecutionContext.setCurrentUserId(userId);
        try {
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
        } finally {
            ToolExecutionContext.clear();
        }
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
     * 根据数据库中的工具配置记录，构建 DeepSeek API 所需的工具定义对象。
     * 包括设置工具类型、名称、描述和参数，并在 JSON 解析失败时回退到代码实现中的默认参数。
     *
     * @param dbTool 数据库中的工具配置实体，包含工具名称、描述和参数等信息
     * @return ToolDefinition 构建完成的工具定义对象，可供 DeepSeek API 使用
     */
    private ToolDefinition buildFromDatabase(Tool dbTool) {
        // 创建 ToolDefinition 对象并设置工具类型
        ToolDefinition definition = new ToolDefinition();        
        definition.setType("function");
    
        // 创建 Function 对象用于封装工具的具体信息
        ToolDefinition.Function function = new ToolDefinition.Function();
            
        // 设置工具名称和描述
        function.setName(dbTool.getToolName());
        function.setDescription(dbTool.getDescription());
    
        // 解析数据库存储的 JSON 格式参数
        if (dbTool.getParameters() != null) {
            try {
                Map<String, Object> params = objectMapper.readValue(
                        dbTool.getParameters(), new TypeReference<Map<String, Object>>() {}
                );
                // 设置解析后的参数
                function.setParameters(params);
            } catch (Exception e) {
                log.error("[FUNCTION_CALLING] 解析工具参数失败：toolName={}", dbTool.getToolName(), e);
                // 回退策略：使用代码实现中定义的默认参数
                AbstractTool impl = toolRegistry.getImplementation(dbTool.getToolName());
                if (impl != null) {
                    function.setParameters(impl.getParameters());
                }
            }
        }
        // 将 Function 对象设置到 ToolDefinition 中
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
        
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        
        try {
            // 1. 从ToolRegistry中获取工具
            AbstractTool tool = toolRegistry.getImplementation(toolName);
            if (tool == null) {
                errorMessage = "工具不存在";
                log.error("[FUNCTION_CALLING] 工具不存在 - name={}", toolName);
                return "错误：工具不存在 - " + toolName;
            }

            if (arguments == null || arguments.trim().isEmpty()) {
                errorMessage = "工具参数为空";
                log.error("[FUNCTION_CALLING] 工具参数为空 - name={}", toolName);
                return "错误：工具参数为空 - " + toolName;
            }
            // 2. 解析参数（JSON字符串->Map）
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(arguments, Map.class);

            // 3. 执行工具
            String result = tool.execute(params);
            success = true;
            
            return result;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("[FUNCTION_CALLING] 工具执行失败 - name={}, error={}", toolName,
                    e.getMessage());
            return "错误：工具执行失败 - " + e.getMessage();
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("[TOOL_EXECUTION] toolName={}, success={}, durationMs={}, errorMessage={}",
                    toolName, success, durationMs, errorMessage);
        }
    }

    /**
     * 带工具的流式对话
     * 处理支持 Function Calling 的流式对话请求，通过迭代方式处理多轮工具调用，
     * 直到 AI 返回最终结果或达到最大迭代次数。支持实时推送流式事件到客户端。
     *
     * @param messages 对话消息列表，包含历史对话上下文
     * @param eventConsumer 事件消费者，用于接收和推送流式事件（如消息片段、工具调用开始/结束等）
     * @return StreamingChatResult 流式对话结果，包含最终回复、Token 统计信息和需要保存的消息列表
     * @throws BusinessException 当没有可用工具、API 返回异常或超过最大迭代次数时抛出
     */
    @Override
    public StreamingChatResult streamChatWithTools(
            Long userId,
            List<DeepSeekMessage> messages,
            Consumer<StreamEvent> eventConsumer) {
        
        ToolExecutionContext.setCurrentUserId(userId);
        try {
            log.info("[FUNCTION_CALLING][STREAM] 开始带工具的流式对话 - messageCount={}", messages.size());
            
            // 存储需要保存到数据库的消息（包括带 tool_calls 的 assistant 消息和 tool 消息）
            List<DeepSeekMessage> messagesToSave = new ArrayList<>();
            // 获取所有可用的工具定义
            List<ToolDefinition> toolDefinitions = getToolDefinitions();

            if (toolDefinitions.isEmpty()) {
                log.warn("[FUNCTION_CALLING][STREAM] 没有可用工具，无法进行流式工具对话");
                throw new BusinessException(ResultCode.INTERNAL_ERROR);
            }

            // 初始化 Token 统计变量
            int totalPromptTokens = 0;
            int totalCompletionTokens = 0;
            int totalTokens = 0;

            // 开始迭代处理工具调用
            int iterations = 0;
            while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
                iterations++;
                log.info("[FUNCTION_CALLING][STREAM] 当前迭代次数 - iteration={}", iterations);
    
                // 创建当前轮次的累积器，用于收集流式响应的各个部分
                StreamingRoundAccumulator accumulator = new StreamingRoundAccumulator();
                
                // 获取 DeepSeek API 的流式响应
                Flux<DeepSeekClient.ToolStreamChunk> stream = deepSeekClient.chatStreamWithTools(messages, toolDefinitions);
    
                try {
                    // 遍历流式响应的每个 chunk
                    for (DeepSeekClient.ToolStreamChunk chunk : stream.toIterable()) {
                        // 跳过空 chunk
                        if (chunk == null) {
                            continue;
                        }
    
                        // 处理结束 chunk，提取 finishReason 和 usage 信息
                        if (chunk.isEnd()) {
                            if (chunk.getFinishReason() != null) {
                                accumulator.setFinishReason(chunk.getFinishReason());
                            }
                            if (chunk.getUsage() != null) {
                                accumulator.setUsage(chunk.getUsage());
                            }
                            continue;
                        }
    
                        // 处理内容 chunk，推送到前端
                        if (chunk.getContent() != null) {
                            accumulator.appendContent(chunk.getContent());
                            accumulator.appendStreamChunk(chunk.getContent());
                            if (shouldFlushStreamMessage(accumulator.streamChunkBuffer)) {
                                String output = accumulator.getStreamChunkBuffer();

                                log.info("[FUNCTION_CALLING][STREAM] 推送消息片段 - iteration={}, chunkLength={}, accumulatedLength={}",
                                        iterations, output.length(), accumulator.getContent().length());

                                eventConsumer.accept(StreamEvent.message(output));
                                accumulator.clearStreamChunkBuffer();
                            }
                        }
    
                        // 处理工具调用 chunk
                        if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                            accumulator.appendToolCalls(chunk.getToolCalls());
                        }
    
                        // 处理结束原因
                        if (chunk.getFinishReason() != null) {
                            accumulator.setFinishReason(chunk.getFinishReason());
                        }
                    }

                    // 在每轮结束前把剩余 buffer 冲掉
                    if (!accumulator.getStreamChunkBuffer().isEmpty()) {
                        String output = accumulator.getStreamChunkBuffer();

                        log.info("[FUNCTION_CALLING][STREAM] 推送最后消息片段 - iteration={}, chunkLength={}, accumulatedLength={}",
                                iterations, output.length(), accumulator.getContent().length());

                        eventConsumer.accept(StreamEvent.message(output));
                        accumulator.clearStreamChunkBuffer();
                    }

                } catch (Exception e) {
                    log.error("[FUNCTION_CALLING][STREAM] 流式响应处理异常", e);
                    throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
                }
    
                // 累加 Token 使用统计
                if (accumulator.getUsage() != null) {
                    DeepSeekClient.StreamResponse.StreamUsage usage = accumulator.getUsage();
                    totalPromptTokens += usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                    totalCompletionTokens += usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                    totalTokens += usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
                }
    
                // 获取当前轮次的累积结果
                String roundContent = accumulator.getContent();
                
                // 构建工具调用列表
                List<ToolCallDTO> toolCalls = accumulator.buildToolCalls();
    
                // 如果没有工具调用，说明 AI 已返回最终结果
                if (toolCalls == null || toolCalls.isEmpty()) {
                    log.info("[FUNCTION_CALLING][STREAM] AI 不需要调用工具，返回最终结果");
                    DeepSeekMessage finalMessage = new DeepSeekMessage();
                    finalMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
                    finalMessage.setContent(roundContent);
                    messagesToSave.add(finalMessage);
                    return new StreamingChatResult(
                            roundContent,
                            totalPromptTokens,
                            totalCompletionTokens,
                            totalTokens,
                            messagesToSave
                    );
                }
    
                log.info("[FUNCTION_CALLING][STREAM] AI 需要调用{}个工具", toolCalls.size());
    
                // 将 AI 的回复（包含 tool_calls）添加到对话历史和待保存列表
                DeepSeekMessage assistantMessage = new DeepSeekMessage();
                assistantMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
                // 如果内容为空字符串，设置为 null（DeepSeek API 要求）
                assistantMessage.setContent(roundContent != null && !roundContent.isEmpty() ? roundContent : null);
                assistantMessage.setToolCalls(toolCalls);
                messages.add(assistantMessage);
                // 记录带 tool_calls 的消息
                messagesToSave.add(assistantMessage);
                
                // 遍历工具调用列表，执行每个工具并收集结果
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
                    // 创建工具响应消息
                    DeepSeekMessage toolMessage = DeepSeekMessage.toolMessage(toolCall.getId(), result);
                    messages.add(toolMessage);
                    // 记录 tool 消息到数据库
                    messagesToSave.add(toolMessage);
                }
            }

            // 超过最大迭代次数，抛出异常
            log.error("[FUNCTION_CALLING][STREAM] 工具调用超过最大次数 - maxIterations={}", BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
            throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    /**
     * 判断当前流式消息缓冲是否应立即输出。
     *
     * <p>当前策略：
     * <ul>
     *     <li>缓冲长度达到最小阈值时输出</li>
     *     <li>遇到换行或常见中文句读时提前输出</li>
     * </ul>
     *
     * @param buffer 当前缓冲内容
     * @return true 表示应立即输出
     */
    private boolean shouldFlushStreamMessage(StringBuilder buffer) {
        if (buffer == null || buffer.length() == 0) {
            return false;
        }

        if (buffer.length() >= STREAM_MESSAGE_FLUSH_MIN_LENGTH) {
            return true;
        }

        char lastChar = buffer.charAt(buffer.length() - 1);
        return lastChar == '\n'
                || lastChar == '。'
                || lastChar == '！'
                || lastChar == '？'
                || lastChar == '；'
                || lastChar == '：';
    }
    
    /**
     * 单轮流式聚合器
     * 把一轮流式分片拼成完整结果：完整文本、完整 toolCalls、finishReason、usage
     */
    @lombok.Data
    private static class StreamingRoundAccumulator {
        // 流式输出缓冲
        private final StringBuilder streamChunkBuffer = new StringBuilder();
        
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
        /**
         * 追加流式输出缓冲内容。
         *
         * @param content 增量内容
         */
        public void appendStreamChunk(String content) {
            if (content != null) {
                streamChunkBuffer.append(content);
            }
        }

        /**
         * 获取当前流式输出缓冲内容。
         *
         * @return 缓冲内容
         */
        public String getStreamChunkBuffer() {
            return streamChunkBuffer.toString();
        }

        /**
         * 清空流式输出缓冲。
         */
        public void clearStreamChunkBuffer() {
            streamChunkBuffer.setLength(0);
        }
        
        /**
         * 累积工具调用
         * 处理流式响应中的工具调用增量数据，根据 index 索引将分片数据拼接成完整的工具调用信息。
         * 支持多个工具调用的并行累积，每个工具调用通过唯一的 index 标识进行区分。
         *
         * @param deltas 工具调用增量列表，包含流式分片的工具调用数据
         */
        public void appendToolCalls(List<DeepSeekClient.StreamResponse.StreamToolCallDelta> deltas) {
            // 空值检查
            if (deltas == null) {
                return;
            }
            // 遍历所有工具调用增量
            for (DeepSeekClient.StreamResponse.StreamToolCallDelta delta : deltas) {
                // 跳过无效的增量数据
                if (delta == null || delta.getIndex() == null) {
                    continue;
                }
                // 根据 index 获取或创建对应的缓冲区
                ToolCallBuffer buffer = toolCallBuffers.computeIfAbsent(delta.getIndex(), k -> new ToolCallBuffer());
                // 设置工具调用 ID
                if (delta.getId() != null) {
                    buffer.id = delta.getId();
                }
                // 设置工具类型
                if (delta.getType() != null) {
                    buffer.type = delta.getType();
                }
                // 处理函数相关信息
                if (delta.getFunction() != null) {
                    DeepSeekClient.StreamResponse.StreamFunctionDelta function = delta.getFunction();
                    // 设置函数名称
                    if (function.getName() != null) {
                        buffer.functionName = function.getName();
                    }
                    // 累积函数字数参数（流式拼接）
                    if (function.getArguments() != null) {
                        buffer.argumentsBuilder.append(function.getArguments());
                    }
                }
            }
        }
    
        public String getContent() {
            return contentBuilder.toString();
        }
    
        /**
         * 构建工具调用列表
         * 遍历所有工具调用缓冲区，将流式拼接完成的工具调用信息转换为 ToolCallDTO 对象列表。
         * 只返回 id 不为空的完整工具调用。
         *
         * @return List<ToolCallDTO> 工具调用列表，包含所有已完成的工具调用信息
         */
        public List<ToolCallDTO> buildToolCalls() {
            // 存储构建完成的工具调用列表
            List<ToolCallDTO> result = new ArrayList<>();
            // 遍历所有工具调用缓冲区
            for (Map.Entry<Integer, ToolCallBuffer> entry : toolCallBuffers.entrySet()) {
                ToolCallBuffer buffer = entry.getValue();
                // 跳过 id 为空的工具调用（不完整的调用）
                if (buffer.id == null) {
                    continue;
                }
                // 构建 ToolCallDTO 对象
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

