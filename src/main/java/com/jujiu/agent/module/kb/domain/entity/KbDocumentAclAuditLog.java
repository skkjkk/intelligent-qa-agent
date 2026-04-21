package com.jujiu.agent.module.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 15:45
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_document_acl_audit_log")
public class KbDocumentAclAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("operator_user_id")
    private Long operatorUserId;

    @TableField("action")
    private String action;

    @TableField("principal_type")
    private String principalType;

    @TableField("principal_id")
    private String principalId;

    @TableField("permission")
    private String permission;

    @TableField("reason")
    private String reason;

    @TableField("created_at")
    private 
    LocalDateTime createdAt;
}
