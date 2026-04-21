package com.jujiu.agent.module.chat.infrastructure.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.StreamResponse;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.ToolStreamChunk;
import com.jujiu.agent.shared.constant.BusinessConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 流式解析器。
 * <p>
 * 专门负责 SSE 文本行解析，避免 Provider 门面直接处理协议细节。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeepSeekStreamParser {

    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /**
     * 解析普通流式内容行。
     *
     * @param line SSE 行
     * @return 文本内容
     */
    public String parseContentLine(String line) {
        try {
            String data = stripSsePrefix(line);
            if (BusinessConstants.SSE_DONE.equals(data)) {
                return "";
            }

            StreamResponse response = objectMapper.readValue(data, StreamResponse.class);
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return "";
            }

            StreamResponse.StreamDelta delta = response.getChoices().get(0).getDelta();
            return delta == null || delta.getContent() == null ? "" : delta.getContent();
        } catch (Exception e) {
            log.error("[LLM][DEEPSEEK][STREAM_CHAT] 解析流式内容失败 - line={}", line, e);
            return "";
        }
    }

    /**
     * 解析工具流式内容行。
     *
     * @param line SSE 行
     * @return 工具流式 chunk
     */
    public ToolStreamChunk parseToolLine(String line) {
        try {
            String data = stripSsePrefix(line);
            if (BusinessConstants.SSE_DONE.equals(data)) {
                return ToolStreamChunk.end("stop", null);
            }

            StreamResponse response = objectMapper.readValue(data, StreamResponse.class);
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return ToolStreamChunk.delta(null, null, null);
            }

            StreamResponse.StreamChoice choice = response.getChoices().get(0);
            if (choice == null) {
                return ToolStreamChunk.delta(null, null, null);
            }

            StreamResponse.StreamDelta delta = choice.getDelta();
            String content = delta != null ? delta.getContent() : null;

            if (response.getUsage() != null) {
                return ToolStreamChunk.end(choice.getFinishReason(), response.getUsage());
            }

            return ToolStreamChunk.delta(
                    content,
                    delta != null ? delta.getToolCalls() : null,
                    choice.getFinishReason()
            );
        } catch (Exception e) {
            log.error("[LLM][DEEPSEEK][STREAM_CHAT_WITH_TOOLS] 解析流式工具响应失败 - line={}", line, e);
            return ToolStreamChunk.delta(null, null, null);
        }
    }

    /**
     * 去掉 SSE 前缀。
     *
     * @param line 原始行
     * @return 纯数据内容
     */
    private String stripSsePrefix(String line) {
        return line != null && line.startsWith("data: ") ? line.substring(6) : line;
    }
}
