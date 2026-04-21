package com.jujiu.agent.module.kb.api.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:25
 */
@Data
@Builder
public class KbDocumentSharingOverviewResponse {
    private Long documentId;
    private String title;
    private Long ownerUserId;
    private String visibility;
    private LocalDateTime createdAt;

    private Boolean readable;
    private Boolean manageable;
    private Boolean shareable;

    private List<KbDocumentAclGrantItemResponse> userGrants;
    private List<KbDocumentAclGrantItemResponse> groupGrants;
    private List<KbDocumentGroupResponse> sharedGroups;
}
