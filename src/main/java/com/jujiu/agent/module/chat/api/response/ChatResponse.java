package com.jujiu.agent.module.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天响应 DTO
 * <p>
 * 用于封装 AI 助手对用户提问的回复内容，包含会话 ID、消息 ID、回复内容等信息。
 * </p>
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:47
 */
@Data
@Builder
@Schema(description = "聊天响应", title = "AI 助手回复信息")
public class ChatResponse {
    
    /**
     * 会话 ID（业务标识）
     */
    @Schema(description = "会话 ID", title = "所属会话编号", example = "SESSION_20260322_001")
    private String sessionId;
    
    /**
     * 消息 ID（业务标识，UUID 格式）
     */
    @Schema(description = "消息 ID", title = "当前回复的消息编号", example = "MSG_20260322_001")
    private String messageId;
    
    /**
     * AI 助手的回复内容
     */
    @Schema(description = "回复内容", title = "AI 助手的回答文本", example = "在 Spring Boot 中创建项目非常简单...")
    private String reply;
    
    /**
     * 对话轮次（当前会话的第几轮对话）
     */
    @Schema(description = "对话轮次", title = "会话交互次数", example = "5")
    private Integer conversationRound;
    
    /**
     * 回复时间戳
     */
    @Schema(description = "时间戳", title = "回复发送时间", example = "2026-03-22 10:47:30")
    private LocalDateTime timestamp;
}
