package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.response.KbDocumentSharingOverviewResponse;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:26
 */
public interface DocumentSharingQueryService {
    /**
     * Get sharing overview.
     *
     * @param userId      the user id
     * @param documentId  the document id
     * @return the sharing overview
     */
    KbDocumentSharingOverviewResponse getSharingOverview(Long userId, Long documentId);
}
