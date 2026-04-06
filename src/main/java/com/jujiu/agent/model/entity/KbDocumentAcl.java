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
 * 文档访问控制实体类
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:38
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_document_acl")
@Schema(description = "文档访问控制实体", title = "KbDocumentAcl")
public class KbDocumentAcl {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("principal_type")
    private String principalType;

    @TableField("principal_id")
    private String principalId;

    @TableField("permission")
    private String permission;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
