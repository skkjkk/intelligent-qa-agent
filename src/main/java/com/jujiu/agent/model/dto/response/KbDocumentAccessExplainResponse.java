package com.jujiu.agent.model.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:32
 */
@Data
@Builder
public class KbDocumentAccessExplainResponse {
    private Long documentId;
    private Long userId;
    private Boolean visible;
    private String reason;
    private String detail;
}
