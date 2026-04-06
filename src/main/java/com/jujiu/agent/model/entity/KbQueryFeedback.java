package com.jujiu.agent.model.entity;

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
 * 知识库查询反馈实体类
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:37
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_query_feedback")
@Schema(description = "知识库查询反馈实体", title = "KbQueryFeedback")
public class KbQueryFeedback {
    
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("query_log_id")
    private Long queryLogId;


    @TableField("user_id")
    private Long userId;

    @TableField("helpful")
    private Integer helpful;

    @TableField("rating")
    private Integer rating;

    @TableField("feedback_content")
    private String feedbackContent;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
