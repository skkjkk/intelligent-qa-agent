package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSeek provider 消息 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepSeekProviderMessage {
    /** 角色。 */
    private String role;

    /** 内容。 */
    private String content;

    /** 工具调用 ID。 */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** 工具调用列表。 */
    @JsonProperty("tool_calls")
    private List<DeepSeekToolCall> toolCalls;
}
