package com.jujiu.agent.module.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表响应 DTO
 * <p>
 * 用于在会话列表中展示单个会话的摘要信息，包含会话 ID、标题、最后一条消息等关键信息。
 * </p>
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 14:27
 */
@Data
@Builder
@Schema(description = "会话列表响应", title = "会话摘要信息")
public class SessionResponse {
    
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
     * 最后一条消息内容（用于快速预览）
     */
    @Schema(description = "最后一条消息", title = "最近一次交互内容", example = "谢谢你的帮助！")
    private String lastMessage;
    
    /**
     * 消息总数（该会话包含的消息数量）
     */
    @Schema(description = "消息总数", title = "会话消息计数", example = "25")
    private Integer messageCount;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间", title = "会话创建时间", example = "2026-03-22 14:27:00")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间（最后一条消息的发送时间）
     */
    @Schema(description = "更新时间", title = "最后更新时问", example = "2026-03-22 14:30:00")
    private LocalDateTime updatedAt;
}
