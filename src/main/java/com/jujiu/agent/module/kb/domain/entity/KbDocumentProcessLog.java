package com.jujiu.agent.module.kb.domain.entity;

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
 * 文档处理日志实体类
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:40
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_document_process_log")
@Schema(description = "文档处理日志实体", title = "KbDocumentProcessLog")
public class KbDocumentProcessLog {


    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;
    
    @TableField("stage")
    private String stage;
    
    @TableField("status")
    private String status;

    @TableField("message")
    private String message;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
