package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.response.KbDocumentAccessExplainResponse;

public interface DocumentAccessExplainService {

    KbDocumentAccessExplainResponse explainAccess(Long userId, Long documentId);

}
