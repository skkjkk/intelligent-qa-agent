package com.jujiu.agent.module.chat.infrastructure.llm;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:41
 */
public interface LlmClient {
    
    /**
     * 获取模型提供方名称
     * @return 模型提供方名称
     */
    String getProviderName();
    
    /**
     * 调用模型
     * @param messages 消息列表
     * @return 模型回复结果
     */
    LlmResult chat(List<LlmMessage> messages);

    /**
     * 调用模型并返回普通流式事件。
     *
     * @param messages 消息列表
     * @return 普通流式事件
     */
    Flux<LlmStreamEvent> streamChat(List<LlmMessage> messages);
    
    /**
     * 调用模型（包含工具）
     * @param messages 消息列表
     * @param tools 工具定义
     * @return 模型回复结果
     */
    LlmResult chatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools);
    
    /**
     * 调用模型（包含工具）并返回流事件
     * @param messages 消息列表
     * @param tools 工具定义
     * @return 流事件
     */
    Flux<LlmStreamEvent> streamChatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools);
}
