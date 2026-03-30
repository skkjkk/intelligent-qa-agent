package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话详情响应 DTO
 * <p>
 * 用于返回完整的会话详情信息，包括会话基本信息和该会话下的所有消息记录。
 * </p>
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 11:28
 */
@Data
@Builder
@Schema(description = "会话详情响应", title = "完整会话信息（含消息列表）")
public class SessionDetailResponse {
    
    /**
     * 会话 ID（业务标识）
     */
    @Schema(description = "会话 ID", title = "业务会话编号", example = "SESSION_20260322_001")
    private String sessionId;
    
    /**
     * 会话标题
     */
    @Schema(description = "会话标题", title = "对话主题", example = "关于 Spring Boot 的咨询")
    private String title;
    
    /**
     * 消息列表（按时间顺序排列的所有对话记录）
     */
    @Schema(description = "消息列表", title = "会话中的所有消息记录")
    private List<MessageVO> messages;
    
    /**
     * 消息视图对象
     * <p>
     * 用于在会话详情中展示单条消息的简化信息。
     * </p>
     */
    @Data
    @Builder
    @Schema(description = "消息视图", title = "单条消息信息")
    public static class MessageVO {
        
        /**
         * 消息 ID（业务标识，UUID 格式）
         */
        @Schema(description = "消息 ID", title = "业务消息编号", example = "MSG_20260322_001")
        private String messageId;
        
        /**
         * 角色类型：user（用户）或 assistant（AI 助手）
         */
        @Schema(description = "角色类型", title = "消息发送者", example = "user", allowableValues = {"user", "assistant"})
        private String role;
        
        /**
         * 消息内容
         */
        @Schema(description = "消息内容", title = "对话文本内容", example = "如何使用 Spring Boot 创建项目？")
        private String content;
        
        /**
         * 消息发送时间戳
         */
        @Schema(description = "时间戳", title = "消息发送时间", example = "2026-03-22 11:28:30")
        private LocalDateTime timestamp;

        /**
         * 工具调用信息（JSON字符串）
         */
        @Schema(description = "工具调用信息", title = "tool_calls JSON字符串")
        private String toolCalls;
    }
}
