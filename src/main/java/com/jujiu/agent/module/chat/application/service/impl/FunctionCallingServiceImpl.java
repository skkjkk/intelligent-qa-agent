package com.jujiu.agent.module.chat.application.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekClient;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekResult;
import com.jujiu.agent.shared.constant.BusinessConstants;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekMessage;
import com.jujiu.agent.module.chat.infrastructure.deepseek.ToolCallDTO;
import com.jujiu.agent.module.chat.infrastructure.deepseek.ToolDefinition;
import com.jujiu.agent.module.tool.domain.entity.Tool;
import com.jujiu.agent.module.chat.application.service.FunctionCallingService;

import com.jujiu.agent.module.tool.runtime.AbstractTool;
import com.jujiu.agent.module.tool.runtime.ToolExecutionContext;
import com.jujiu.agent.module.tool.application.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Function Calling 服务实现类。
 * <p>
 * 负责处理支持工具调用的对话流程，包括普通对话和流式对话两种模式。
 * 核心逻辑：通过迭代方式与 DeepSeek API 交互，当 AI 决定调用工具时，
 * 自动执行对应工具并将结果回传给 AI，直到获得最终回复或达到最大迭代次数。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/25
 */
@Service
@Slf4j
public class FunctionCallingServiceImpl implements FunctionCallingService {

    /**
     * 流式消息缓冲区最小刷新长度。
     * 当缓冲区内容达到此长度，或遇到换行/句读符号时，会立即推送给客户端。
     */
    private static final int STREAM_MESSAGE_FLUSH_MIN_LENGTH = 32;

    /**
     * 工具注册表，用于获取已启用的工具实例及数据库配置。
     */
    private final ToolRegistry toolRegistry;

    /**
     * DeepSeek API 客户端，负责发起对话和流式对话请求。
     */
    private final DeepSeekClient deepSeekClient;

    /**
     * Jackson 对象映射器，用于 JSON 序列化与反序列化（如工具参数解析）。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造方法。
     *
     * @param toolRegistry   工具注册表
     * @param deepSeekClient DeepSeek API 客户端
     * @param objectMapper   Jackson 对象映射器
     */
    public FunctionCallingServiceImpl(ToolRegistry toolRegistry, DeepSeekClient deepSeekClient, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 带工具的普通对话。
     * <p>
     * 通过多轮迭代与 DeepSeek API 交互，处理 AI 触发的工具调用，
     * 直到 AI 返回最终结果或超过最大迭代次数。
     *
     * @param userId   当前用户 ID
     * @param messages 对话消息列表（会被修改以加入 assistant/tool 消息）
     * @return DeepSeekResult AI 的最终回复结果
     * @throws BusinessException 当超过最大迭代次数时抛出
     */
    @Override
    public DeepSeekResult chatWithTools(Long userId, List<DeepSeekMessage> messages) {
        // 1. 设置当前用户 ID 到线程上下文
        ToolExecutionContext.setCurrentUserId(userId);
        try {
            log.info("[FUNCTION_CALLING] 开始带工具的对话 - messageCount={}", messages.size());

            // 2. 获取所有可用的工具定义
            List<ToolDefinition> toolDefinitions = getToolDefinitions();
            log.info("[FUNCTION_CALLING] 获取到工具数量 - count={}", toolDefinitions.size());

            // 3. 如果没有可用工具，降级为普通对话
            if (toolDefinitions.isEmpty()) {
                log.warn("[FUNCTION_CALLING] 没有可用的工具，将进行普通对话");
                return deepSeekClient.chat(messages);
            }

            // 4. 开始多轮迭代处理工具调用
            int iterations = 0;
            while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
                iterations++;
                log.info("[FUNCTION_CALLING] 当前迭代次数 - iteration={}", iterations);

                // 5. 调用 DeepSeek API（带工具定义）
                DeepSeekResult result = deepSeekClient.chatWithTools(messages, toolDefinitions);

                // 6. 检查 AI 是否要求调用工具
                List<ToolCallDTO> toolCalls = result.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    log.info("[FUNCTION_CALLING] AI不需要调用工具，返回最终结果");
                    return result;
                }

                // 7. 将 assistant 的消息（包含 tool_calls）加入对话历史
                DeepSeekMessage assistantMessage = new DeepSeekMessage();
                assistantMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
                // 7.1 如果内容为空字符串，设置为 null（DeepSeek API 要求）
                String content = result.getReply();
                assistantMessage.setContent(content != null && !content.isEmpty() ? content : null);
                assistantMessage.setToolCalls(toolCalls);
                messages.add(assistantMessage);

                // 8. 执行所有工具调用并将结果回传
                log.info("[FUNCTION_CALLING] AI需要调用{}个工具", result.getToolCalls().size());
                for (ToolCallDTO toolCall : result.getToolCalls()) {
                    // 8.1 执行单个工具
                    String toolResult = executeTool(toolCall);
                    // 8.2 将工具结果作为 tool 消息加入对话历史
                    messages.add(DeepSeekMessage.toolMessage(toolCall.getId(), toolResult));
                }
            }

            // 9. 超过最大迭代次数，抛出业务异常
            log.error("[FUNCTION_CALLING] 工具调用超过最大次数 - maxIterations={}", BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
            throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
        } finally {
            // 10. 清理线程上下文
            ToolExecutionContext.clear();
        }
    }

    /**
     * 获取所有可用的工具定义。
     * <p>
     * 从 ToolRegistry 获取已启用的工具列表，并将每个工具转换为 DeepSeek API 所需的格式。
     *
     * @return 工具定义列表
     */
    private List<ToolDefinition> getToolDefinitions() {
        // 1. 获取所有启用的工具
        List<Tool> enabledTools = toolRegistry.getEnabledTools();
        List<ToolDefinition> toolDefinitions = new ArrayList<>();

        // 2. 遍历每个工具并构建 ToolDefinition
        for (Tool dbTool : enabledTools) {
            ToolDefinition definition = buildFromDatabase(dbTool);
            toolDefinitions.add(definition);
        }

        // 3. 记录加载数量并返回
        log.info("[FUNCTION_CALLING] 从数据库加载工具配置 - count={}", toolDefinitions.size());
        return toolDefinitions;
    }

    /**
     * 从数据库配置构建 ToolDefinition。
     * <p>
     * 根据数据库中的工具配置记录，构建 DeepSeek API 所需的工具定义对象。
     * 包括设置工具类型、名称、描述和参数，并在 JSON 解析失败时回退到代码实现中的默认参数。
     *
     * @param dbTool 数据库中的工具配置实体，包含工具名称、描述和参数等信息
     * @return 构建完成的工具定义对象，可供 DeepSeek API 使用
     */
    private ToolDefinition buildFromDatabase(Tool dbTool) {
        // 1. 创建 ToolDefinition 对象并设置工具类型
        ToolDefinition definition = new ToolDefinition();
        definition.setType("function");

        // 2. 创建 Function 对象用于封装工具的具体信息
        ToolDefinition.Function function = new ToolDefinition.Function();

        // 3. 设置工具名称和描述
        function.setName(dbTool.getToolName());
        function.setDescription(dbTool.getDescription());

        // 4. 解析数据库存储的 JSON 格式参数
        if (dbTool.getParameters() != null) {
            try {
                Map<String, Object> params = objectMapper.readValue(
                        dbTool.getParameters(), new TypeReference<Map<String, Object>>() {}
                );
                // 4.1 设置解析后的参数
                function.setParameters(params);
            } catch (Exception e) {
                log.error("[FUNCTION_CALLING] 解析工具参数失败：toolName={}", dbTool.getToolName(), e);
                // 4.2 回退策略：使用代码实现中定义的默认参数
                AbstractTool impl = toolRegistry.getImplementation(dbTool.getToolName());
                if (impl != null) {
                    function.setParameters(impl.getParameters());
                }
            }
        }

        // 5. 将 Function 对象设置到 ToolDefinition 中并返回
        definition.setFunction(function);
        return definition;
    }
    
    /**
     * 执行单个工具调用。
     * <p>
     * 根据 toolCall 中的工具名称和参数，从 ToolRegistry 获取实际实现并执行，
     * 返回执行结果字符串。若工具不存在、参数为空或执行异常，会返回相应的错误信息。
     *
     * @param toolCall 工具调用信息
     * @return 工具执行结果或错误说明
     */
    private String executeTool(ToolCallDTO toolCall) {
        // 1. 提取工具名称和原始参数 JSON
        String toolName = toolCall.getFunction().getName();
        String arguments = toolCall.getFunction().getArguments();

        // 2. 初始化执行统计变量
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            // 3. 从 ToolRegistry 中获取工具实现
            AbstractTool tool = toolRegistry.getImplementation(toolName);
            if (tool == null) {
                errorMessage = "工具不存在";
                log.error("[FUNCTION_CALLING] 工具不存在 - name={}", toolName);
                return "错误：工具不存在 - " + toolName;
            }

            // 4. 校验参数是否为空
            if (arguments == null || arguments.trim().isEmpty()) {
                errorMessage = "工具参数为空";
                log.error("[FUNCTION_CALLING] 工具参数为空 - name={}", toolName);
                return "错误：工具参数为空 - " + toolName;
            }

            // 5. 解析参数（JSON 字符串 -> Map）
            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(arguments, Map.class);

            // 6. 执行工具并返回结果
            String result = tool.execute(params);
            success = true;
            return result;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("[FUNCTION_CALLING] 工具执行失败 - name={}, error={}", toolName, e.getMessage());
            return "错误：工具执行失败 - " + e.getMessage();
        } finally {
            // 7. 记录工具执行日志
            long durationMs = System.currentTimeMillis() - start;
            log.info("[TOOL_EXECUTION] toolName={}, success={}, durationMs={}, errorMessage={}",
                    toolName, success, durationMs, errorMessage);
        }
    }

    /**
     * 带工具的流式对话。
     * <p>
     * 处理支持 Function Calling 的流式对话请求，通过迭代方式处理多轮工具调用，
     * 直到 AI 返回最终结果或达到最大迭代次数。支持实时推送流式事件到客户端。
     *
     * @param userId        当前用户 ID
     * @param messages      对话消息列表，包含历史对话上下文
     * @param eventConsumer 事件消费者，用于接收和推送流式事件（如消息片段、工具调用开始/结束等）
     * @return 流式对话结果，包含最终回复、Token 统计信息和需要保存的消息列表
     * @throws BusinessException 当没有可用工具、API 返回异常或超过最大迭代次数时抛出
     */
    @Override
    public StreamingChatResult streamChatWithTools(
            Long userId,
            List<DeepSeekMessage> messages,
            Consumer<StreamEvent> eventConsumer) {

        // 1. 设置当前用户 ID 到线程上下文
        ToolExecutionContext.setCurrentUserId(userId);
        try {
            log.info("[FUNCTION_CALLING][STREAM] 开始带工具的流式对话 - messageCount={}", messages.size());

            // 2. 初始化需要保存到数据库的消息列表
            List<DeepSeekMessage> messagesToSave = new ArrayList<>();

            // 3. 获取所有可用的工具定义
            List<ToolDefinition> toolDefinitions = getToolDefinitions();
            if (toolDefinitions.isEmpty()) {
                log.warn("[FUNCTION_CALLING][STREAM] 没有可用工具，无法进行流式工具对话");
                throw new BusinessException(ResultCode.INTERNAL_ERROR);
            }

            // 4. 初始化 Token 统计变量
            int totalPromptTokens = 0;
            int totalCompletionTokens = 0;
            int totalTokens = 0;

            // 5. 开始迭代处理工具调用
            int iterations = 0;
            while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
                iterations++;
                log.info("[FUNCTION_CALLING][STREAM] 当前迭代次数 - iteration={}", iterations);

                // 5.1 创建当前轮次的累积器，用于收集流式响应的各个部分
                StreamingRoundAccumulator accumulator = new StreamingRoundAccumulator();

                // 5.2 获取 DeepSeek API 的流式响应
                Flux<DeepSeekClient.ToolStreamChunk> stream = deepSeekClient.chatStreamWithTools(messages, toolDefinitions);

                try {
                    // 5.3 遍历流式响应的每个 chunk
                    for (DeepSeekClient.ToolStreamChunk chunk : stream.toIterable()) {
                        // 5.3.1 跳过空 chunk
                        if (chunk == null) {
                            continue;
                        }

                        // 5.3.2 处理结束 chunk，提取 finishReason 和 usage 信息
                        if (chunk.isEnd()) {
                            if (chunk.getFinishReason() != null) {
                                accumulator.setFinishReason(chunk.getFinishReason());
                            }
                            if (chunk.getUsage() != null) {
                                accumulator.setUsage(chunk.getUsage());
                            }
                            continue;
                        }

                        // 5.3.3 处理内容 chunk，推送到前端
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

                        // 5.3.4 处理工具调用 chunk
                        if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                            accumulator.appendToolCalls(chunk.getToolCalls());
                        }

                        // 5.3.5 处理结束原因
                        if (chunk.getFinishReason() != null) {
                            accumulator.setFinishReason(chunk.getFinishReason());
                        }
                    }

                    // 5.4 在每轮结束前把剩余 buffer 冲掉
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

                // 5.5 累加 Token 使用统计
                if (accumulator.getUsage() != null) {
                    DeepSeekClient.StreamResponse.StreamUsage usage = accumulator.getUsage();
                    totalPromptTokens += usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                    totalCompletionTokens += usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                    totalTokens += usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
                }

                // 5.6 获取当前轮次的累积文本内容和工具调用列表
                String roundContent = accumulator.getContent();
                List<ToolCallDTO> toolCalls = accumulator.buildToolCalls();

                // 5.7 如果没有工具调用，说明 AI 已返回最终结果
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

                // 5.8 将 AI 的回复（包含 tool_calls）添加到对话历史和待保存列表
                DeepSeekMessage assistantMessage = new DeepSeekMessage();
                assistantMessage.setRole(DeepSeekMessage.MessageRole.ASSISTANT);
                // 5.8.1 如果内容为空字符串，设置为 null（DeepSeek API 要求）
                assistantMessage.setContent(roundContent != null && !roundContent.isEmpty() ? roundContent : null);
                assistantMessage.setToolCalls(toolCalls);
                messages.add(assistantMessage);
                messagesToSave.add(assistantMessage);

                // 5.9 遍历工具调用列表，执行每个工具并收集结果
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

                    // 5.9.1 创建工具响应消息并加入对话历史
                    DeepSeekMessage toolMessage = DeepSeekMessage.toolMessage(toolCall.getId(), result);
                    messages.add(toolMessage);
                    // 5.9.2 记录 tool 消息到待保存列表
                    messagesToSave.add(toolMessage);
                }
            }

            // 6. 超过最大迭代次数，抛出业务异常
            log.error("[FUNCTION_CALLING][STREAM] 工具调用超过最大次数 - maxIterations={}", BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
            throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
        } finally {
            // 7. 清理线程上下文
            ToolExecutionContext.clear();
        }
    }

    /**
     * 判断当前流式消息缓冲是否应立即输出。
     * <p>
     * 当前策略：
     * <ul>
     *     <li>缓冲长度达到最小阈值时输出</li>
     *     <li>遇到换行或常见中文句读时提前输出</li>
     * </ul>
     *
     * @param buffer 当前缓冲内容
     * @return true 表示应立即输出
     */
    private boolean shouldFlushStreamMessage(StringBuilder buffer) {
        // 1. 空缓冲区直接返回 false
        if (buffer == null || buffer.length() == 0) {
            return false;
        }

        // 2. 长度达到阈值立即输出
        if (buffer.length() >= STREAM_MESSAGE_FLUSH_MIN_LENGTH) {
            return true;
        }

        // 3. 遇到换行或中文句读符号时提前输出
        char lastChar = buffer.charAt(buffer.length() - 1);
        return lastChar == '\n'
                || lastChar == '。'
                || lastChar == '！'
                || lastChar == '？'
                || lastChar == '；'
                || lastChar == '：';
    }
    
    /**
     * 单轮流式聚合器。
     * <p>
     * 负责把一轮流式分片拼成完整结果，包括完整文本、完整 toolCalls、finishReason 和 usage。
     */
    @lombok.Data
    private static class StreamingRoundAccumulator {

        /**
         * 流式输出缓冲，用于暂存待推送到前端的消息片段。
         */
        private final StringBuilder streamChunkBuffer = new StringBuilder();

        /**
         * 累计文本内容，存储该轮 AI 回复的完整文本。
         */
        private final StringBuilder contentBuilder = new StringBuilder();

        /**
         * 累计工具调用，按 index 分组存储每个工具调用的流式分片。
         */
        private final Map<Integer, ToolCallBuffer> toolCallBuffers = new LinkedHashMap<>();

        /**
         * 轮次结束原因，如 "stop" 或 "tool_calls"。
         */
        private String finishReason;

        /**
         * 轮次 Token 使用情况。
         */
        private DeepSeekClient.StreamResponse.StreamUsage usage;

        /**
         * 追加文本内容到累计内容构建器。
         *
         * @param content 增量文本内容
         */
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
         * @return 缓冲内容字符串
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
         * 累积工具调用。
         * <p>
         * 处理流式响应中的工具调用增量数据，根据 index 索引将分片数据拼接成完整的工具调用信息。
         * 支持多个工具调用的并行累积，每个工具调用通过唯一的 index 标识进行区分。
         *
         * @param deltas 工具调用增量列表，包含流式分片的工具调用数据
         */
        public void appendToolCalls(List<DeepSeekClient.StreamResponse.StreamToolCallDelta> deltas) {
            // 1. 空值检查
            if (deltas == null) {
                return;
            }

            // 2. 遍历所有工具调用增量
            for (DeepSeekClient.StreamResponse.StreamToolCallDelta delta : deltas) {
                // 2.1 跳过无效的增量数据
                if (delta == null || delta.getIndex() == null) {
                    continue;
                }

                // 2.2 根据 index 获取或创建对应的缓冲区
                ToolCallBuffer buffer = toolCallBuffers.computeIfAbsent(delta.getIndex(), k -> new ToolCallBuffer());

                // 2.3 设置工具调用 ID
                if (delta.getId() != null) {
                    buffer.id = delta.getId();
                }

                // 2.4 设置工具类型
                if (delta.getType() != null) {
                    buffer.type = delta.getType();
                }

                // 2.5 处理函数相关信息
                if (delta.getFunction() != null) {
                    DeepSeekClient.StreamResponse.StreamFunctionDelta function = delta.getFunction();

                    // 2.5.1 设置函数名称
                    if (function.getName() != null) {
                        buffer.functionName = function.getName();
                    }

                    // 2.5.2 累积函数字数参数（流式拼接）
                    if (function.getArguments() != null) {
                        buffer.argumentsBuilder.append(function.getArguments());
                    }
                }
            }
        }

        /**
         * 获取当前累计的完整文本内容。
         *
         * @return 完整文本内容
         */
        public String getContent() {
            return contentBuilder.toString();
        }

        /**
         * 构建工具调用列表。
         * <p>
         * 遍历所有工具调用缓冲区，将流式拼接完成的工具调用信息转换为 ToolCallDTO 对象列表。
         * 只返回 id 不为空的完整工具调用。
         *
         * @return 工具调用列表，包含所有已完成的工具调用信息
         */
        public List<ToolCallDTO> buildToolCalls() {
            // 1. 存储构建完成的工具调用列表
            List<ToolCallDTO> result = new ArrayList<>();

            // 2. 遍历所有工具调用缓冲区
            for (Map.Entry<Integer, ToolCallBuffer> entry : toolCallBuffers.entrySet()) {
                ToolCallBuffer buffer = entry.getValue();

                // 2.1 跳过 id 为空的工具调用（不完整的调用）
                if (buffer.id == null) {
                    continue;
                }

                // 2.2 构建 ToolCallDTO 对象
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
     * 工具调用缓冲区。
     * <p>
     * 用于拼接流式分片的工具调用信息，包括 ID、类型、函数名和参数。
     */
    private static class ToolCallBuffer {

        /**
         * 工具调用 ID。
         */
        private String id;

        /**
         * 工具调用类型，如 "function"。
         */
        private String type;

        /**
         * 工具函数名称。
         */
        private String functionName;

        /**
         * 工具参数构建器，用于流式拼接函数字数参数 JSON。
         */
        private final StringBuilder argumentsBuilder = new StringBuilder();
    }
    
    
}

