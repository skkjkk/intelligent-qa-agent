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
 * 文档标签关联实体类
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:29
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_document_tag")
@Schema(description = "文档标签关联实体", title = "KbDocumentTag")
public class KbDocumentTag {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "document_id")
    private Long documentId;

    @TableField(value = "tag_id")
    private Long tagId;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
