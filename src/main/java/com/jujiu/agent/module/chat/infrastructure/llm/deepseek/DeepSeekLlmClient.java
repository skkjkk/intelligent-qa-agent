package com.jujiu.agent.module.chat.infrastructure.llm.deepseek;

import com.jujiu.agent.module.chat.infrastructure.llm.LlmClient;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmMessage;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmResult;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmStreamEvent;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmToolDefinition;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * DeepSeek 的 LLM Provider 门面。
 * <p>
 * 该类只负责统一编排：
 * 构建请求 -> 委托 HTTP 执行器 -> 委托映射器/解析器转换结果。
 * DeepSeek 协议 DTO、HTTP 细节与流式解析都已拆到独立类中。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:42
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.providers.deepseek.enabled", havingValue = "true", matchIfMissing = true)
public class DeepSeekLlmClient implements LlmClient {

    /** DeepSeek HTTP 执行器。 */
    private final DeepSeekHttpExecutor httpExecutor;

    /** DeepSeek 协议映射器。 */
    private final DeepSeekMessageMapper messageMapper;

    /** DeepSeek 流式解析器。 */
    private final DeepSeekStreamParser streamParser;

    @Override
    public String getProviderName() {
        return "deepseek";
    }

    @Override
    public LlmResult chat(List<LlmMessage> messages) {
        DeepSeekRequest request = messageMapper.buildChatRequest(messages);
        return messageMapper.toLlmResult(httpExecutor.executeChat(request));
    }

    @Override
    public Flux<LlmStreamEvent> streamChat(List<LlmMessage> messages) {
        DeepSeekRequest request = messageMapper.buildStreamChatRequest(messages);
        return httpExecutor.streamChat(request)
                .map(streamParser::parseContentLine)
                .filter(content -> content != null && !content.isEmpty())
                .map(LlmStreamEvent::content);
    }

    @Override
    public LlmResult chatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        DeepSeekRequest request = messageMapper.buildToolChatRequest(messages, tools);
        return messageMapper.toLlmResult(httpExecutor.executeToolChat(request));
    }

    @Override
    public Flux<LlmStreamEvent> streamChatWithTools(List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        DeepSeekRequest request = messageMapper.buildToolStreamChatRequest(messages, tools);
        return httpExecutor.streamToolChat(request)
                .map(streamParser::parseToolLine)
                .filter(chunk -> chunk != null && (chunk.isEnd()
                        || (chunk.getContent() != null && !chunk.getContent().isEmpty())
                        || (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty())))
                .map(messageMapper::toLlmStreamEvent);
    }
}
