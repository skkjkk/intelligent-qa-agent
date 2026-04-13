package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.response.KbDocumentAclAuditLogResponse;

import java.util.List;

public interface DocumentAclAuditService {
    /**
     * Log a grant action.
     *
     * @param documentId     the ID of the document
     * @param operatorUserId the ID of the operator user
     * @param principalType  the type of the principal
     * @param principalId    the ID of the principal
     * @param permission     the permission granted
     */
    void logAclGrant(Long documentId,
                  Long operatorUserId,
                  String principalType,
                  String principalId,
                  String permission);

    /**
     * Log a revoke action.
     *
     * @param documentId     the ID of the document
     * @param operatorUserId the ID of the operator user
     * @param principalType  the type of the principal
     * @param principalId    the ID of the principal
     * @param permission     the permission revoked
     */
    void logAclRevoke(Long documentId,
                   Long operatorUserId,
                   String principalType,
                   String principalId,
                   String permission);

    /**
     * Log an access denied action.
     *
     * @param documentId the ID of the document
     * @param operatorUserId the ID of the operator user
     * @param reason     the reason for the access denial
     */
    void logAccessDenied(Long documentId,
                         Long operatorUserId,
                         String reason);


    void logGroupBind(Long documentId,
                      Long operatorUserId,
                      Long groupId);

    void logGroupUnbind(Long documentId,
                        Long operatorUserId,
                        Long groupId);
    /**
     * List audit logs for a user or document.
     *
     * @param userId       the ID of the user
     * @param documentId   the ID of the document
     * @param action       the action to filter by
     * @return a list of audit log responses
     */
    List<KbDocumentAclAuditLogResponse> listAuditLogs(Long userId, Long documentId, String action);
}
