package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.response.KbDocumentAccessExplainResponse;

public interface DocumentAccessExplainService {

    KbDocumentAccessExplainResponse explainAccess(Long userId, Long documentId);

}
