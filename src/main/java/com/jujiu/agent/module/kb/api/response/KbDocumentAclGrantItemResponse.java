package com.jujiu.agent.module.kb.api.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:24
 */
@Data
@Builder
public class KbDocumentAclGrantItemResponse {
    private Long aclId;
    private String principalType;
    private String principalId;
    private String permission;
    private LocalDateTime createdAt;
}
