package com.jujiu.agent.module.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:09
 */
@Data
@TableName("kb_document_group")
public class KbDocumentGroup {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("group_id")
    private Long groupId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
