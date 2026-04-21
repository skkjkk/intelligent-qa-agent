package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek 响应 DTO。
 */
@Data
public class DeepSeekResponse {
    /** 选择列表。 */
    private List<Choice> choices;

    /** token 用量。 */
    private Usage usage;

    @Data
    public static class Choice {
        /** 返回消息。 */
        private DeepSeekProviderMessage message;

        /** 完成原因。 */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Usage {
        /** prompt tokens。 */
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        /** completion tokens。 */
        @JsonProperty("completion_tokens")
        private int completionTokens;

        /** total tokens。 */
        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}
