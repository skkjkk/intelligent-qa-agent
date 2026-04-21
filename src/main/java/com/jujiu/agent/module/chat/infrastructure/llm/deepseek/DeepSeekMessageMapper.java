package com.jujiu.agent.module.chat.infrastructure.llm.deepseek;

import com.jujiu.agent.module.chat.infrastructure.config.DeepSeekProperties;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmMessage;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmResult;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmStreamEvent;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmToolCall;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmToolDefinition;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekProviderMessage;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekRequest;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekResponse;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekToolCall;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekToolDefinition;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.ToolStreamChunk;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * DeepSeek 协议映射器。
 * <p>
 * 负责统一 LLM 抽象对象和 DeepSeek provider DTO 之间的双向转换。
 */
@Component
public class DeepSeekMessageMapper {

    /** DeepSeek 配置。 */
    private final DeepSeekProperties deepSeekProperties;

    public DeepSeekMessageMapper(DeepSeekProperties deepSeekProperties) {
        this.deepSeekProperties = deepSeekProperties;
    }

    /**
     * 构建普通同步请求。
     *
     * @param messages 统一消息
     * @return DeepSeek 请求
     */
    public DeepSeekRequest buildChatRequest(List<LlmMessage> messages) {
        return buildRequest(messages, null, false);
    }

    /**
     * 构建普通流式请求。
     *
     * @param messages 统一消息
     * @return DeepSeek 请求
     */
    public DeepSeekRequest buildStreamChatRequest(List<LlmMessage> messages) {
        return buildRequest(messages, null, true);
    }

    /**
     * 构建带工具同步请求。
     *
     * @param messages 统一消息
     * @param tools 统一工具定义
     * @return DeepSeek 请求
     */
    public DeepSeekRequest buildToolChatRequest(List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        return buildRequest(messages, tools, false);
    }

    /**
     * 构建带工具流式请求。
     *
     * @param messages 统一消息
     * @param tools 统一工具定义
     * @return DeepSeek 请求
     */
    public DeepSeekRequest buildToolStreamChatRequest(List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        return buildRequest(messages, tools, true);
    }

    /**
     * 将 DeepSeek 同步响应映射为统一结果。
     *
     * @param response DeepSeek 响应
     * @return 统一结果
     */
    public LlmResult toLlmResult(DeepSeekResponse response) {
        DeepSeekResponse.Choice choice = extractFirstChoice(response);
        DeepSeekResponse.Usage usage = response.getUsage();

        LlmResult llmResult = new LlmResult();
        llmResult.setReply(choice.getMessage().getContent());
        llmResult.setPromptTokens(usage != null ? usage.getPromptTokens() : 0);
        llmResult.setCompletionTokens(usage != null ? usage.getCompletionTokens() : 0);
        llmResult.setTotalTokens(usage != null ? usage.getTotalTokens() : 0);
        llmResult.setToolCalls(toLlmToolCalls(choice.getMessage().getToolCalls()));
        llmResult.setFinishReason(choice.getFinishReason() != null
                ? choice.getFinishReason()
                : (choice.getMessage().getToolCalls() == null || choice.getMessage().getToolCalls().isEmpty() ? "stop" : "tool_calls"));
        return llmResult;
    }

    /**
     * 将工具流式 chunk 映射为统一流式事件。
     *
     * @param chunk DeepSeek 工具流式 chunk
     * @return 统一流式事件
     */
    public LlmStreamEvent toLlmStreamEvent(ToolStreamChunk chunk) {
        if (chunk == null) {
            return LlmStreamEvent.content("");
        }

        if (chunk.isEnd()) {
            Integer promptTokens = chunk.getUsage() != null ? chunk.getUsage().getPromptTokens() : null;
            Integer completionTokens = chunk.getUsage() != null ? chunk.getUsage().getCompletionTokens() : null;
            Integer totalTokens = chunk.getUsage() != null ? chunk.getUsage().getTotalTokens() : null;
            return LlmStreamEvent.finish(chunk.getFinishReason(), promptTokens, completionTokens, totalTokens);
        }

        if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
            List<LlmStreamEvent.ToolCallDelta> toolCallDeltas = chunk.getToolCalls().stream()
                    .filter(Objects::nonNull)
                    .map(delta -> {
                        LlmStreamEvent.ToolCallDelta toolCallDelta = new LlmStreamEvent.ToolCallDelta();
                        toolCallDelta.setIndex(delta.getIndex());
                        toolCallDelta.setId(delta.getId());
                        toolCallDelta.setType(delta.getType());

                        if (delta.getFunction() != null) {
                            LlmStreamEvent.FunctionDelta functionDelta = new LlmStreamEvent.FunctionDelta();
                            functionDelta.setName(delta.getFunction().getName());
                            functionDelta.setArguments(delta.getFunction().getArguments());
                            toolCallDelta.setFunction(functionDelta);
                        }
                        return toolCallDelta;
                    })
                    .toList();
            return LlmStreamEvent.toolCallDelta(toolCallDeltas);
        }

        return LlmStreamEvent.content(chunk.getContent());
    }

    /**
     * 构建 DeepSeek 请求。
     *
     * @param messages 统一消息
     * @param tools 统一工具
     * @param stream 是否流式
     * @return DeepSeek 请求
     */
    private DeepSeekRequest buildRequest(List<LlmMessage> messages, List<LlmToolDefinition> tools, boolean stream) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(deepSeekProperties.getModel());
        request.setMessages(toProviderMessages(messages));
        request.setTools(toProviderTools(tools));
        request.setStream(stream ? Boolean.TRUE : null);
        request.setTemperature(deepSeekProperties.getTemperature());
        return request;
    }

    /**
     * 提取第一个 choice。
     *
     * @param response DeepSeek 响应
     * @return 第一个 choice
     */
    private DeepSeekResponse.Choice extractFirstChoice(DeepSeekResponse response) {
        return response.getChoices().get(0);
    }

    /**
     * 将统一消息映射为 provider 消息。
     *
     * @param messages 统一消息
     * @return provider 消息
     */
    private List<DeepSeekProviderMessage> toProviderMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream()
                .filter(Objects::nonNull)
                .map(message -> {
                    DeepSeekProviderMessage providerMessage = new DeepSeekProviderMessage();
                    providerMessage.setRole(message.getRole());
                    providerMessage.setContent(message.getContent());
                    providerMessage.setToolCallId(message.getToolCallId());
                    providerMessage.setToolCalls(toProviderToolCalls(message.getToolCalls()));
                    return providerMessage;
                })
                .toList();
    }

    /**
     * 将统一工具定义映射为 provider 工具定义。
     *
     * @param tools 统一工具定义
     * @return provider 工具定义
     */
    private List<DeepSeekToolDefinition> toProviderTools(List<LlmToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }

        return tools.stream()
                .filter(Objects::nonNull)
                .map(tool -> {
                    DeepSeekToolDefinition definition = new DeepSeekToolDefinition();
                    definition.setType(tool.getType());

                    DeepSeekToolDefinition.Function function = new DeepSeekToolDefinition.Function();
                    function.setName(tool.getFunction().getName());
                    function.setDescription(tool.getFunction().getDescription());
                    function.setParameters(tool.getFunction().getParameters());
                    definition.setFunction(function);
                    return definition;
                })
                .toList();
    }

    /**
     * 将统一工具调用映射为 provider 工具调用。
     *
     * @param toolCalls 统一工具调用
     * @return provider 工具调用
     */
    private List<DeepSeekToolCall> toProviderToolCalls(List<LlmToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        return toolCalls.stream()
                .filter(Objects::nonNull)
                .map(toolCall -> {
                    DeepSeekToolCall providerToolCall = new DeepSeekToolCall();
                    providerToolCall.setId(toolCall.getId());
                    providerToolCall.setType(toolCall.getType());

                    DeepSeekToolCall.Function function = new DeepSeekToolCall.Function();
                    function.setName(toolCall.getName());
                    function.setArguments(toolCall.getArguments());
                    providerToolCall.setFunction(function);
                    return providerToolCall;
                })
                .toList();
    }

    /**
     * 将 provider 工具调用映射为统一工具调用。
     *
     * @param toolCalls provider 工具调用
     * @return 统一工具调用
     */
    private List<LlmToolCall> toLlmToolCalls(List<DeepSeekToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        return toolCalls.stream()
                .filter(Objects::nonNull)
                .map(toolCall -> {
                    LlmToolCall llmToolCall = new LlmToolCall();
                    llmToolCall.setId(toolCall.getId());
                    llmToolCall.setType(toolCall.getType());
                    if (toolCall.getFunction() != null) {
                        llmToolCall.setName(toolCall.getFunction().getName());
                        llmToolCall.setArguments(toolCall.getFunction().getArguments());
                    }
                    return llmToolCall;
                })
                .toList();
    }
}
