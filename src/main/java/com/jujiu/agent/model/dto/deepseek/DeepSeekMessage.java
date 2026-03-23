package com.jujiu.agent.model.dto.deepseek;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Schema(description = "对话消息", title = "单条对话消息")
public class DeepSeekMessage {
    
    /**
     * 角色类型
     * 标识消息发送者的身份
     * 可选值："user"（用户）, "assistant"（AI 助手）, "system"（系统）
     * 示例："user", "assistant"
     */
    @Schema(description = "角色类型", title = "消息发送者", example = "user")
    private String role;

    /**
     * 消息内容
     * 具体的对话文本内容
     * 示例："你好，请问如何使用 Spring Boot？"
     */
    @Schema(description = "消息内容", title = "对话文本", example = "你好，请问如何使用 Spring Boot？")
    private String content;
}
