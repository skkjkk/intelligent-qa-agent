package com.jujiu.agent.module.chat.application.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.application.service.FunctionCallingService;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmClientRouter;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmMessage;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmResult;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmStreamEvent;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmToolCall;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmToolDefinition;
import com.jujiu.agent.module.tool.application.registry.ToolRegistry;
import com.jujiu.agent.module.tool.domain.entity.Tool;
import com.jujiu.agent.module.tool.runtime.AbstractTool;
import com.jujiu.agent.module.tool.runtime.ToolExecutionContext;
import com.jujiu.agent.shared.constant.BusinessConstants;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
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
 * 该实现已经切换到统一的 LLM Provider 抽象层，业务流程只使用 {@link LlmMessage}、
 * {@link LlmResult} 和 {@link LlmToolCall}，不再直接依赖 DeepSeek DTO。
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
     * 当缓冲区内容达到该阈值或遇到句读符号时，会立刻推送给客户端。
     */
    private static final int STREAM_MESSAGE_FLUSH_MIN_LENGTH = 32;

    /** 工具注册表，用于获取数据库配置和运行时实现。 */
    private final ToolRegistry toolRegistry;

    /** JSON 序列化器，用于解析工具参数。 */
    private final ObjectMapper objectMapper;

    /** LLM 客户端路由器，用于屏蔽不同 provider 的实现差异。 */
    private final LlmClientRouter llmClientRouter;

    public FunctionCallingServiceImpl(ToolRegistry toolRegistry,
                                      ObjectMapper objectMapper,
                                      LlmClientRouter llmClientRouter) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.llmClientRouter = llmClientRouter;
    }

    /**
     * 带工具的普通对话。
     *
     * @param userId 当前用户 ID
     * @param messages 对话消息列表
     * @return LLM 最终回复
     */
    @Override
    public LlmResult chatWithTools(Long userId, List<LlmMessage> messages) {
        ToolExecutionContext.setCurrentUserId(userId);
        try {
            log.info("[FUNCTION_CALLING] 开始带工具的非流式对话 - userId={}, messageCount={}", userId, messages.size());

            List<LlmToolDefinition> toolDefinitions = getLlmToolDefinitions();
            log.info("[FUNCTION_CALLING] 已加载工具定义 - count={}", toolDefinitions.size());

            if (toolDefinitions.isEmpty()) {
                log.warn("[FUNCTION_CALLING] 当前没有可用工具，降级为普通对话 - userId={}", userId);
                return llmClientRouter.getDefault().chat(messages);
            }

            int iterations = 0;
            while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
                iterations++;
                log.info("[FUNCTION_CALLING] 发起模型调用 - iteration={}, messageCount={}, toolCount={}",
                        iterations, messages.size(), toolDefinitions.size());

                LlmResult llmResult = llmClientRouter.getDefault().chatWithTools(messages, toolDefinitions);
                List<LlmToolCall> toolCalls = llmResult.getToolCalls();

                if (toolCalls == null || toolCalls.isEmpty()) {
                    log.info("[FUNCTION_CALLING] 模型返回最终结果，无需继续调用工具 - iteration={}, totalTokens={}",
                            iterations, llmResult.getTotalTokens());
                    return llmResult;
                }

                // 把 assistant 的 tool_calls 写回上下文，供下一轮模型继续补全。
                LlmMessage assistantMessage = new LlmMessage();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(normalizeAssistantContent(llmResult.getReply()));
                assistantMessage.setToolCalls(toolCalls);
                messages.add(assistantMessage);

                log.info("[FUNCTION_CALLING] 模型请求调用工具 - iteration={}, toolCallCount={}",
                        iterations, toolCalls.size());

                for (LlmToolCall toolCall : toolCalls) {
                    String toolResult = executeTool(toolCall);
                    messages.add(LlmMessage.tool(toolCall.getId(), toolResult));
                }
            }

            log.error("[FUNCTION_CALLING] 工具调用超过最大迭代次数 - maxIterations={}",
                    BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
            throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    /**
     * 带工具的流式对话。
     *
     * @param userId 当前用户 ID
     * @param messages 统一消息上下文
     * @param eventConsumer SSE 事件消费者
     * @return 最终流式结果
     */
    @Override
    public StreamingChatResult streamChatWithTools(Long userId,
                                                   List<LlmMessage> messages,
                                                   Consumer<StreamEvent> eventConsumer) {
        ToolExecutionContext.setCurrentUserId(userId);
        try {
            log.info("[FUNCTION_CALLING][STREAM] 开始带工具的流式对话 - userId={}, messageCount={}", userId, messages.size());

            List<LlmToolDefinition> toolDefinitions = getLlmToolDefinitions();
            if (toolDefinitions.isEmpty()) {
                log.error("[FUNCTION_CALLING][STREAM] 当前没有可用工具，无法执行流式工具调用 - userId={}", userId);
                throw new BusinessException(ResultCode.INTERNAL_ERROR);
            }

            List<LlmMessage> messagesToSave = new ArrayList<>();
            int totalPromptTokens = 0;
            int totalCompletionTokens = 0;
            int totalTokens = 0;

            int iterations = 0;
            while (iterations < BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS) {
                iterations++;
                log.info("[FUNCTION_CALLING][STREAM] 发起流式模型调用 - iteration={}, messageCount={}, toolCount={}",
                        iterations, messages.size(), toolDefinitions.size());

                StreamingRoundAccumulator accumulator = new StreamingRoundAccumulator();
                Flux<LlmStreamEvent> stream = llmClientRouter.getDefault().streamChatWithTools(messages, toolDefinitions);

                try {
                    for (LlmStreamEvent event : stream.toIterable()) {
                        if (event == null || event.getType() == null) {
                            continue;
                        }

                        switch (event.getType()) {
                            case CONTENT -> handleContentEvent(iterations, accumulator, eventConsumer, event);
                            case TOOL_CALL_DELTA -> accumulator.appendToolCalls(event.getToolCallDeltas());
                            case FINISH -> accumulator.updateFinish(event);
                            default -> log.debug("[FUNCTION_CALLING][STREAM] 忽略未处理事件类型 - type={}", event.getType());
                        }
                    }

                    flushRemainingStreamChunk(iterations, accumulator, eventConsumer);
                } catch (Exception e) {
                    log.error("[FUNCTION_CALLING][STREAM] 处理流式事件失败 - iteration={}", iterations, e);
                    throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
                }

                totalPromptTokens += accumulator.getPromptTokens();
                totalCompletionTokens += accumulator.getCompletionTokens();
                totalTokens += accumulator.getTotalTokens();

                String roundContent = accumulator.getContent();
                List<LlmToolCall> toolCalls = accumulator.buildToolCalls();
                if (toolCalls.isEmpty()) {
                    log.info("[FUNCTION_CALLING][STREAM] 当前轮次无需调用工具，返回最终结果 - iteration={}, totalTokens={}",
                            iterations, totalTokens);

                    LlmMessage finalAssistantMessage = LlmMessage.assistant(roundContent);
                    messagesToSave.add(finalAssistantMessage);
                    return new StreamingChatResult(
                            roundContent,
                            totalPromptTokens,
                            totalCompletionTokens,
                            totalTokens,
                            messagesToSave
                    );
                }

                log.info("[FUNCTION_CALLING][STREAM] 当前轮次需要调用工具 - iteration={}, toolCallCount={}",
                        iterations, toolCalls.size());

                LlmMessage assistantMessage = new LlmMessage();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(normalizeAssistantContent(roundContent));
                assistantMessage.setToolCalls(toolCalls);
                messages.add(assistantMessage);
                messagesToSave.add(assistantMessage);

                for (LlmToolCall toolCall : toolCalls) {
                    String toolName = toolCall.getName();
                    eventConsumer.accept(StreamEvent.toolStart(toolName));

                    String toolResult = executeTool(toolCall);
                    eventConsumer.accept(StreamEvent.toolEnd(toolName, !toolResult.startsWith("错误："), null));

                    LlmMessage toolMessage = LlmMessage.tool(toolCall.getId(), toolResult);
                    messages.add(toolMessage);
                    messagesToSave.add(toolMessage);
                }
            }

            log.error("[FUNCTION_CALLING][STREAM] 工具调用超过最大迭代次数 - maxIterations={}",
                    BusinessConstants.FUNCTION_CALLING_MAX_ITERATIONS);
            throw new BusinessException(ResultCode.FUNCTION_CALLING_MAX_ITERATIONS);
        } finally {
            ToolExecutionContext.clear();
        }
    }

    /**
     * 处理内容增量事件，并按缓冲策略对外推送。
     *
     * @param iteration 当前轮次
     * @param accumulator 单轮累积器
     * @param eventConsumer 事件消费者
     * @param event 统一流式事件
     */
    private void handleContentEvent(int iteration,
                                    StreamingRoundAccumulator accumulator,
                                    Consumer<StreamEvent> eventConsumer,
                                    LlmStreamEvent event) {
        String content = event.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }

        accumulator.appendContent(content);
        accumulator.appendStreamChunk(content);
        if (shouldFlushStreamMessage(accumulator.streamChunkBuffer)) {
            String output = accumulator.getStreamChunkBuffer();
            log.info("[FUNCTION_CALLING][STREAM] 推送消息片段 - iteration={}, chunkLength={}, accumulatedLength={}",
                    iteration, output.length(), accumulator.getContent().length());
            eventConsumer.accept(StreamEvent.message(output));
            accumulator.clearStreamChunkBuffer();
        }
    }

    /**
     * 把当前轮次未刷新的流式内容冲刷给前端。
     *
     * @param iteration 当前轮次
     * @param accumulator 单轮累积器
     * @param eventConsumer 事件消费者
     */
    private void flushRemainingStreamChunk(int iteration,
                                           StreamingRoundAccumulator accumulator,
                                           Consumer<StreamEvent> eventConsumer) {
        if (accumulator.getStreamChunkBuffer().isEmpty()) {
            return;
        }

        String output = accumulator.getStreamChunkBuffer();
        log.info("[FUNCTION_CALLING][STREAM] 推送最后消息片段 - iteration={}, chunkLength={}, accumulatedLength={}",
                iteration, output.length(), accumulator.getContent().length());
        eventConsumer.accept(StreamEvent.message(output));
        accumulator.clearStreamChunkBuffer();
    }

    /**
     * 从数据库配置和代码实现构建统一工具定义。
     *
     * @return 统一工具定义列表
     */
    private List<LlmToolDefinition> getLlmToolDefinitions() {
        List<Tool> enabledTools = toolRegistry.getEnabledTools();
        List<LlmToolDefinition> toolDefinitions = new ArrayList<>();

        for (Tool dbTool : enabledTools) {
            toolDefinitions.add(buildLlmToolDefinitionFromDatabase(dbTool));
        }

        log.info("[FUNCTION_CALLING] 从数据库加载工具定义完成 - count={}", toolDefinitions.size());
        return toolDefinitions;
    }

    /**
     * 将数据库工具配置转为统一工具定义。
     *
     * @param dbTool 工具配置实体
     * @return 统一工具定义
     */
    private LlmToolDefinition buildLlmToolDefinitionFromDatabase(Tool dbTool) {
        LlmToolDefinition definition = new LlmToolDefinition();
        definition.setType("function");

        LlmToolDefinition.Function function = new LlmToolDefinition.Function();
        function.setName(dbTool.getToolName());
        function.setDescription(dbTool.getDescription());

        if (dbTool.getParameters() != null) {
            try {
                Map<String, Object> params = objectMapper.readValue(
                        dbTool.getParameters(), new TypeReference<Map<String, Object>>() {}
                );
                function.setParameters(params);
            } catch (Exception e) {
                log.error("[FUNCTION_CALLING] 解析工具参数失败，回退代码定义 - toolName={}", dbTool.getToolName(), e);
                AbstractTool implementation = toolRegistry.getImplementation(dbTool.getToolName());
                if (implementation != null) {
                    function.setParameters(implementation.getParameters());
                }
            }
        }

        definition.setFunction(function);
        return definition;
    }

    /**
     * 执行单个工具调用。
     *
     * @param toolCall 统一工具调用对象
     * @return 工具执行结果
     */
    private String executeTool(LlmToolCall toolCall) {
        String toolName = toolCall.getName();
        String arguments = toolCall.getArguments();
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            AbstractTool tool = toolRegistry.getImplementation(toolName);
            if (tool == null) {
                errorMessage = "工具不存在";
                log.error("[FUNCTION_CALLING] 工具不存在 - toolName={}", toolName);
                return "错误：工具不存在 - " + toolName;
            }

            if (arguments == null || arguments.trim().isEmpty()) {
                errorMessage = "工具参数为空";
                log.error("[FUNCTION_CALLING] 工具参数为空 - toolName={}", toolName);
                return "错误：工具参数为空 - " + toolName;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> params = objectMapper.readValue(arguments, Map.class);
            String result = tool.execute(params);
            success = true;
            return result;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("[FUNCTION_CALLING] 工具执行失败 - toolName={}, error={}", toolName, e.getMessage(), e);
            return "错误：工具执行失败 - " + e.getMessage();
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("[TOOL_EXECUTION] toolName={}, success={}, durationMs={}, errorMessage={}",
                    toolName, success, durationMs, errorMessage);
        }
    }

    /**
     * 规范化 assistant 消息内容。
     * <p>
     * 当模型只返回 tool_calls 而没有文本时，这里将空字符串归一为 null，避免 provider 侧的协议校验问题。
     *
     * @param content 原始内容
     * @return 归一化后的内容
     */
    private String normalizeAssistantContent(String content) {
        return content != null && !content.isEmpty() ? content : null;
    }

    /**
     * 判断当前流式消息缓冲是否应立即输出。
     *
     * @param buffer 当前缓冲
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
     * 单轮流式聚合器。
     * <p>
     * 负责在 provider 无关的前提下，把一轮流式返回聚合为完整文本、工具调用和 Token 统计。
     */
    @lombok.Data
    private static class StreamingRoundAccumulator {
        /** 暂存尚未推送给前端的文本缓冲。 */
        private final StringBuilder streamChunkBuffer = new StringBuilder();

        /** 累积当前轮次的完整文本。 */
        private final StringBuilder contentBuilder = new StringBuilder();

        /** 按 index 分组的工具调用缓冲。 */
        private final Map<Integer, ToolCallBuffer> toolCallBuffers = new LinkedHashMap<>();

        /** 当前轮次完成原因。 */
        private String finishReason;

        /** 当前轮次提示词 Token 数。 */
        private int promptTokens;

        /** 当前轮次生成内容 Token 数。 */
        private int completionTokens;

        /** 当前轮次总 Token 数。 */
        private int totalTokens;

        public void appendContent(String content) {
            if (content != null) {
                contentBuilder.append(content);
            }
        }

        public void appendStreamChunk(String content) {
            if (content != null) {
                streamChunkBuffer.append(content);
            }
        }

        public String getStreamChunkBuffer() {
            return streamChunkBuffer.toString();
        }

        public void clearStreamChunkBuffer() {
            streamChunkBuffer.setLength(0);
        }

        public String getContent() {
            return contentBuilder.toString();
        }

        /**
         * 累积工具调用增量。
         *
         * @param deltas 工具调用增量
         */
        public void appendToolCalls(List<LlmStreamEvent.ToolCallDelta> deltas) {
            if (deltas == null || deltas.isEmpty()) {
                return;
            }

            for (LlmStreamEvent.ToolCallDelta delta : deltas) {
                if (delta == null || delta.getIndex() == null) {
                    continue;
                }

                ToolCallBuffer buffer = toolCallBuffers.computeIfAbsent(delta.getIndex(), key -> new ToolCallBuffer());
                if (delta.getId() != null) {
                    buffer.id = delta.getId();
                }
                if (delta.getType() != null) {
                    buffer.type = delta.getType();
                }
                if (delta.getFunction() != null) {
                    if (delta.getFunction().getName() != null) {
                        buffer.functionName = delta.getFunction().getName();
                    }
                    if (delta.getFunction().getArguments() != null) {
                        buffer.argumentsBuilder.append(delta.getFunction().getArguments());
                    }
                }
            }
        }

        /**
         * 更新当前轮次的结束状态和 Token 统计。
         *
         * @param event 结束事件
         */
        public void updateFinish(LlmStreamEvent event) {
            this.finishReason = event.getFinishReason();
            this.promptTokens = event.getPromptTokens() != null ? event.getPromptTokens() : 0;
            this.completionTokens = event.getCompletionTokens() != null ? event.getCompletionTokens() : 0;
            this.totalTokens = event.getTotalTokens() != null ? event.getTotalTokens() : 0;
        }

        /**
         * 将缓冲中的工具调用构造成统一工具调用对象。
         *
         * @return 已拼接完成的工具调用列表
         */
        public List<LlmToolCall> buildToolCalls() {
            List<LlmToolCall> result = new ArrayList<>();

            for (ToolCallBuffer buffer : toolCallBuffers.values()) {
                if (buffer.id == null) {
                    continue;
                }

                LlmToolCall toolCall = new LlmToolCall();
                toolCall.setId(buffer.id);
                toolCall.setType(buffer.type != null ? buffer.type : "function");
                toolCall.setName(buffer.functionName);
                toolCall.setArguments(buffer.argumentsBuilder.toString());
                result.add(toolCall);
            }

            return result;
        }
    }

    /**
     * 流式工具调用缓冲区。
     */
    private static class ToolCallBuffer {
        /** 工具调用 ID。 */
        private String id;

        /** 工具调用类型。 */
        private String type;

        /** 工具名称。 */
        private String functionName;

        /** 逐片拼接的参数 JSON。 */
        private final StringBuilder argumentsBuilder = new StringBuilder();
    }
}
