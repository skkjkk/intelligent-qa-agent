package com.jujiu.agent.module.kb.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.api.response.KbDocumentAclAuditLogResponse;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.domain.entity.KbDocumentAclAuditLog;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentAclAuditLogMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.application.service.DocumentAclAuditService;
import com.jujiu.agent.module.kb.application.service.DocumentAclService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档 ACL 审计服务实现类。负责记录 ACL 授权、撤销及访问拒绝的审计日志。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 15:47
 */
@Service
@Slf4j
public class DocumentAclAuditServiceImpl implements DocumentAclAuditService {
    
    private static final String ACTION_ACL_GRANT = "ACL_GRANT";
    private static final String ACTION_ACL_REVOKE = "ACL_REVOKE";
    private static final String ACTION_GROUP_BIND = "GROUP_BIND";
    private static final String ACTION_GROUP_UNBIND = "GROUP_UNBIND";
    private static final String ACTION_ACCESS_DENIED = "ACCESS_DENIED";

    /** ACL 审计日志仓储。 */
    private final KbDocumentAclAuditLogMapper auditLogRepository;
    private final KbDocumentMapper kbDocumentMapper;
    private final DocumentAclService documentAclService;
    /**
     * 构造方法。
     *
     * @param auditLogRepository ACL 审计日志仓储
     * @param kbDocumentMapper 文档仓储
     * @param documentAclService   文档 ACL 服务
     */
    public DocumentAclAuditServiceImpl(KbDocumentAclAuditLogMapper auditLogRepository,
                                       KbDocumentMapper kbDocumentMapper,
                                       DocumentAclService documentAclService) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.documentAclService = documentAclService;
        this.auditLogRepository = auditLogRepository;
    }
    /**
     * 记录授权审计日志。
     *
     * @param documentId    文档 ID
     * @param operatorUserId 操作人用户 ID
     * @param principalType 主体类型
     * @param principalId   主体标识
     * @param permission    权限类型
     */
    @Override
    public void logAclGrant(Long documentId, 
                            Long operatorUserId, 
                            String principalType, 
                            String principalId, 
                            String permission) {
        // 调用通用保存方法记录授权动作
        save(documentId, operatorUserId, ACTION_ACL_GRANT, principalType, principalId, permission, null);
    }

    /**
     * 记录撤销审计日志。
     *
     * @param documentId    文档 ID
     * @param operatorUserId 操作人用户 ID
     * @param principalType 主体类型
     * @param principalId   主体标识
     * @param permission    权限类型
     */
    @Override
    public void logAclRevoke(Long documentId, 
                             Long operatorUserId, 
                             String principalType, 
                             String principalId, 
                             String permission) {
        // 调用通用保存方法记录撤销动作
        save(documentId, operatorUserId, ACTION_ACL_REVOKE, principalType, principalId, permission, null);
    }

    @Override
    public void logGroupBind(Long documentId, Long operatorUserId, Long groupId) {
        save(documentId, operatorUserId, ACTION_GROUP_BIND, "GROUP", String.valueOf(groupId), "READ", null);
    }

    @Override
    public void logGroupUnbind(Long documentId, Long operatorUserId, Long groupId) {
        save(documentId, operatorUserId, ACTION_GROUP_UNBIND, "GROUP", String.valueOf(groupId), "READ", null);
    }
    /**
     * 记录访问拒绝审计日志。
     *
     * @param documentId     文档 ID
     * @param operatorUserId 操作人用户 ID
     * @param reason         拒绝原因
     */
    @Override
    public void logAccessDenied(Long documentId, Long operatorUserId, String reason) {
        // 调用通用保存方法记录访问拒绝动作
        save(documentId, operatorUserId, ACTION_ACCESS_DENIED, null, null, null, reason);
    }

    @Override
    public List<KbDocumentAclAuditLogResponse> listAuditLogs(Long userId, Long documentId, String action) {
        KbDocument document = requireShareableDocument(userId, documentId);

        LambdaQueryWrapper<KbDocumentAclAuditLog> wrapper = new LambdaQueryWrapper<KbDocumentAclAuditLog>()
                .eq(KbDocumentAclAuditLog::getDocumentId, documentId)
                .orderByDesc(KbDocumentAclAuditLog::getCreatedAt);

        if (action != null && !action.isBlank()) {
            wrapper.eq(KbDocumentAclAuditLog::getAction, action.toUpperCase());
        }

        return auditLogRepository.selectList(wrapper).stream()
                .map(log -> KbDocumentAclAuditLogResponse.builder()
                        .id(log.getId())
                        .documentId(log.getDocumentId())
                        .operatorUserId(log.getOperatorUserId())
                        .action(log.getAction())
                        .principalType(log.getPrincipalType())
                        .principalId(log.getPrincipalId())
                        .permission(log.getPermission())
                        .reason(log.getReason())
                        .createdAt(log.getCreatedAt())
                        .build()
                ).toList();
    }

    private KbDocument requireShareableDocument(Long userId, Long documentId) {
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null || document.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (!documentAclService.canShare(userId, document)) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        return document;
    }



    /**
     * 保存审计日志记录到数据库并输出日志。
     *
     * @param documentId     文档 ID
     * @param operatorUserId 操作人用户 ID
     * @param action         审计动作
     * @param principalType  主体类型
     * @param principalId    主体标识
     * @param permission     权限类型
     * @param reason         拒绝原因
     */
    private void save(Long documentId,
                      Long operatorUserId,
                      String action,
                      String principalType,
                      String principalId,
                      String permission,
                      String reason) {
        // 1. 构建审计日志实体
        KbDocumentAclAuditLog logEntity = KbDocumentAclAuditLog.builder()
                .documentId(documentId)
                .operatorUserId(operatorUserId)
                .action(action)
                .principalType(principalType)
                .principalId(principalId)
                .permission(permission)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();

        // 2. 持久化到数据库
        auditLogRepository.insert(logEntity);

        // 3. 输出审计日志到日志系统
        log.info("[KB][ACL][AUDIT] action={}, documentId={}, operatorUserId={}, principalType={}, principalId={}, permission={}, reason={}",
                action, documentId, operatorUserId, principalType, principalId, permission, reason);
    }
}
