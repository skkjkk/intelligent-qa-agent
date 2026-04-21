package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSeek 工具流式 chunk DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolStreamChunk {
    /** 普通文本增量。 */
    private String content;

    /** 工具调用增量。 */
    private List<StreamResponse.StreamToolCallDelta> toolCalls;

    /** 完成原因。 */
    private String finishReason;

    /** token 使用情况。 */
    private StreamResponse.StreamUsage usage;

    /** 是否结束。 */
    private boolean end;

    public static ToolStreamChunk delta(String content,
                                        List<StreamResponse.StreamToolCallDelta> toolCalls,
                                        String finishReason) {
        return new ToolStreamChunk(content, toolCalls, finishReason, null, false);
    }

    public static ToolStreamChunk end(String finishReason,
                                      StreamResponse.StreamUsage usage) {
        return new ToolStreamChunk(null, null, finishReason, usage, true);
    }
}
