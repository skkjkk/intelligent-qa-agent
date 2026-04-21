package com.jujiu.agent.module.chat.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:31
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmMessage {
    /**
     * 角色类型
     * 标识消息发送者的身份
     * 可选值：""user"（用户）, "assistant"（AI 助手）, "system"（系统）, "tool"（工具返回）
     * 示例："user", "assistant"
     */
    @Schema(description = "角色类型", title = "消息发送者", example = "user")
    @NotBlank(message = "角色不能为空")
    private String role;
    /**
     * 消息内容
     * 具体的对话文本内容
     * 示例："你好，请问如何使用 Spring Boot？"
     */
    @Schema(description = "消息内容", title = "对话文本", example = "你好，请问如何使用 Spring Boot？")
    private String content;
    /**
     * 工具调用列表
     * 当AI判断需要调用工具时返回（仅assistant角色）
     */
    @Schema(description = "工具调用列表", title = "AI请求的工具调用")
    @JsonProperty("tool_calls")
    private List<LlmToolCall> toolCalls;
    
    /**
     * 工具调用 ID
     * 标识工具调用的唯一标识（仅当role为tool时使用） 
     * 示例："tool_call_id_123"
     */
    @Schema(description = "工具调用 ID", title = "工具调用唯一标识", example = "tool_call_id_123")
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /**
     * 创建用户消息
     * @param content 用户的对话文本内容
     * @return user角色的消息对象
     */
    public static LlmMessage user(String content) {
        LlmMessage m = new LlmMessage();
        m.role = "user";
        m.content = content; 
        return m;
    }

    /**
     * 创建系统消息
     * @param content 系统的对话文本内容
     * @return system角色的消息对象
     */
    public static LlmMessage system(String content) {
        LlmMessage m = new LlmMessage();
        m.role = "system";
        m.content = content; 
        return m;
    }

    /**
     * 创建助手消息
     * @param content 助手的对话文本内容
     * @return assistant角色的消息对象
     */
    public static LlmMessage assistant(String content) {
        LlmMessage m = new LlmMessage();
        m.role = "assistant";
        m.content = content; 
        return m;  
    }
    
    /**
     * 创建工具返回消息
     * @param toolCallId 工具调用ID
     * @param content 工具返回的结果
     * @return tool角色的消息对象
     */
    public static LlmMessage tool(String toolCallId, String content) {
        LlmMessage m = new LlmMessage();
        m.role = "tool";
        m.toolCallId = toolCallId;
        m.content = content; 
        return m;
    }
}
