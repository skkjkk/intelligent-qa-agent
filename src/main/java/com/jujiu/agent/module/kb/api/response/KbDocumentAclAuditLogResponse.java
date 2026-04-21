package com.jujiu.agent.module.kb.api.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:13
 */
@Data
@Builder
public class KbDocumentAclAuditLogResponse {
    /**
     * ID
     */
    private Long id;
    /**
     * 文档 ID
     */
    private Long documentId;
    /**
     * 操作用户 ID
     */
    private Long operatorUserId;
    /**
     * 操作类型
     */
    private String action;
    /**
     * 主体类型
     */
    private String principalType;
    /**
     * 主体 ID
     */
    private String principalId;
    /**
     * 权限
     */
    private String permission;
    /**
     * 原因
     */
    private String reason;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
