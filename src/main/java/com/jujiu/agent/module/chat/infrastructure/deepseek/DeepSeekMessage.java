package com.jujiu.agent.module.chat.infrastructure.deepseek;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSeek 对话消息 DTO
 * 
 * 表示单条对话消息，包含角色和内容
 * 用于构建多轮对话的历史记录
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 10:06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)   // null字段不序列化到JSON
@Schema(description = "对话消息", title = "单条对话消息")
public class DeepSeekMessage {
    
    /**
     * 角色类型
     * 标识消息发送者的身份
     * 可选值：""user"（用户）, "assistant"（AI 助手）, "system"（系统）, "tool"（工具返回）
     * 示例："user", "assistant"
     */
    @Schema(description = "角色类型", title = "消息发送者", example = "user")
    @NotBlank(message = "角色不能为空")
    private MessageRole role;

    /**
     * 消息内容
     * 具体的对话文本内容
     * 示例："你好，请问如何使用 Spring Boot？"
     */
    @Schema(description = "消息内容", title = "对话文本", example = "你好，请问如何使用 Spring Boot？")
    private String content;

    
    /**
     * 工具调用 ID
     * 标识工具调用的唯一标识（仅当role为tool时使用） 
     * 示例："tool_call_id_123"
     */
    @Schema(description = "工具调用 ID", title = "工具调用唯一标识", example = "tool_call_id_123")
    @JsonProperty("tool_call_id")
    private String toolCallId;


    /**
     * 工具调用列表
     * 当AI判断需要调用工具时返回（仅assistant角色）
     */
    @Schema(description = "工具调用列表", title = "AI请求的工具调用")
    @JsonProperty("tool_calls")
    private List<ToolCallDTO> toolCalls;
    
    /**
     * 创建工具返回消息
     * @param toolCallId 工具调用ID
     * @param content 工具返回的结果
     * @return tool角色的消息对象
     */
    public static DeepSeekMessage toolMessage(String toolCallId, String content) {
        DeepSeekMessage message = new DeepSeekMessage();
        message.setRole(MessageRole.TOOL);
        message.setToolCallId(toolCallId);
        message.setContent(content);
        return message;
    }

    public enum MessageRole {
        @JsonProperty("user")
        USER("user"),

        @JsonProperty("assistant")
        ASSISTANT("assistant"),

        @JsonProperty("system")
        SYSTEM("system"),

        @JsonProperty("tool")
        TOOL("tool");

        private final String value;

        MessageRole(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * 根据 value 值查找对应的枚举常量
         * @param value 枚举的 value 值（如 "user", "assistant" 等）
         * @return 对应的枚举常量
         * @throws IllegalArgumentException 如果找不到匹配的枚举
         */
        public static MessageRole fromValue(String value) {
            for (MessageRole role : values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Invalid role value: " + value);
        }
    }
}
