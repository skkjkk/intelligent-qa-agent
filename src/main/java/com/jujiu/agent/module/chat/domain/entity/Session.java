package com.jujiu.agent.module.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话信息实体
 * 用于存储用户会话的基本信息，包括会话 ID、用户信息、消息统计等。
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:19
 */
@Data
@TableName("session")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话信息", title = "对话会话记录")
public class Session {
    
    /**
     * 主键 ID（UUID）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID", title = "会话唯一标识", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;
    
    /**
     * 会话 ID（业务标识）
     */
    @Schema(description = "会话 ID", title = "业务会话编号", example = "SESSION_20260322_001")
    private String sessionId;
    
    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID", title = "所属用户 ID", example = "123")
    private Long userId;
    
    /**
     * 会话标题（通常由第一条消息或用户自定义生成）
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
     * 状态：1-活跃，0-已关闭
     */
    @Schema(description = "会话状态", title = "状态：1-活跃，0-已关闭", example = "1")
    private Integer status;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", title = "会话创建时间", example = "2026-03-22 10:19:00")
    private LocalDateTime createdAt;

    /**
     * 更新时间（最后一条消息的发送时间）
     */
    @Schema(description = "更新时间", title = "最后更新时间", example = "2026-03-22 10:30:00")
    private LocalDateTime updatedAt;
}
