package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSeek 流式响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamResponse {
    /** 选择列表。 */
    private List<StreamChoice> choices;

    /** token 用量。 */
    private StreamUsage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamChoice {
        /** 增量对象。 */
        private StreamDelta delta;

        /** 完成原因。 */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamDelta {
        /** 内容增量。 */
        private String content;

        /** 工具调用增量。 */
        @JsonProperty("tool_calls")
        private List<StreamToolCallDelta> toolCalls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamUsage {
        /** prompt tokens。 */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /** completion tokens。 */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /** total tokens。 */
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamToolCallDelta {
        /** 工具调用索引。 */
        private Integer index;

        /** 工具调用 ID。 */
        private String id;

        /** 类型。 */
        private String type;

        /** 函数增量。 */
        private StreamFunctionDelta function;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamFunctionDelta {
        /** 函数名称。 */
        private String name;

        /** 参数增量。 */
        private String arguments;
    }
}
