package com.jujiu.agent.module.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息信息实体
 * <p>
 * 用于存储用户与 AI 助手之间的对话消息记录，包括用户提问和 AI 回复。
 * </p>
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:27
 */
@Data
@Schema(description = "消息信息", title = "对话消息记录")
@TableName("message")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    
    /**
     * 主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID", title = "消息唯一标识", example = "1")
    private Long id;
    
    /**
     * 消息 ID（业务标识，UUID 格式）
     */
    @Schema(description = "消息 ID", title = "业务消息编号", example = "MSG_20260322_001")
    private String messageId;
    
    /**
     * 所属会话 ID（关联到 Session 表）
     */
    @Schema(description = "会话 ID", title = "所属会话编号", example = "SESSION_20260322_001")
    private String sessionId;
    
    /**
     * 角色类型：user（用户）或 assistant（AI 助手）
     */
    @Schema(description = "角色类型", title = "消息发送者", example = "user", allowableValues = {"user", "assistant"})
    private String role;
    
    /**
     * 消息内容（用户问题或 AI 回答）
     */
    @Schema(description = "消息内容", title = "对话文本内容", example = "如何使用 Spring Boot 创建项目？")
    private String content;

    /**
     * Token数量（AI消息时的token消耗）
     */
    @Schema(description = "Token数量", title = "AI消息时的token消耗", example = "150")
    private Integer tokens;
    
    /**
     * 工具调用列表（AI assistant 角色时，记录 tool_calls）
     */
    @TableField("tool_calls")
    @Schema(description = "工具调用列表", title = "AI调用的工具记录")
    // 存 JSON 字符串
    private String toolCalls;

    /**
     * 工具调用ID（role为tool时使用）
     */
    @TableField("tool_call_id")
    @Schema(description = "工具调用ID", title = "关联的tool_call_id")
    private String toolCallId;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间", title = "消息发送时间", example = "2026-03-22 10:27:30")
    private LocalDateTime createdAt;
    
}
