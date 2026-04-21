package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.response.KbDocumentSharingOverviewResponse;

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
