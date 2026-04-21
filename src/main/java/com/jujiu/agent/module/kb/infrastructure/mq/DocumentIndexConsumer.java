package com.jujiu.agent.module.kb.infrastructure.mq;

import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.domain.enums.KbProcessStage;
import com.jujiu.agent.module.kb.domain.event.DocumentIndexEvent;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.application.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 文档索引事件消费者。
 *
 * <p>用于消费文档索引事件，驱动文档后续向量化与 Elasticsearch 索引流程。
 * 当前消费者不直接实现索引细节，而是复用 {@link DocumentService} 中
 * 已有的索引逻辑，保证自动索引与手动索引行为一致。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/12
 */
@Component
@Slf4j
public class DocumentIndexConsumer {
    private final KbDocumentMapper kbDocumentMapper;
    private final DocumentService documentService;
    private final DocumentProcessFailureHandler failureHandler;

    public DocumentIndexConsumer(KbDocumentMapper kbDocumentMapper,
                                 DocumentService documentService,
                                 DocumentProcessFailureHandler failureHandler) {
        this.kbDocumentMapper = kbDocumentMapper;
        this.documentService = documentService;
        this.failureHandler = failureHandler;
    }

    @KafkaListener(topics = "${knowledge-base.kafka.topic-document-index}")
    public void consume(DocumentIndexEvent event) {
        if (event == null || event.getDocumentId() == null) {
            log.warn("[KB][INDEX_EVENT] 收到空索引事件，忽略处理");
            return;
        }
        
        Long documentId = event.getDocumentId();
        Long userId = event.getUserId();
        log.info("[KB][INDEX_EVENT] 收到文档索引事件 - documentId={}, userId={}", documentId, userId);
        
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null || document.getDeleted() == 1) {
            log.warn("[KB][INDEX_EVENT] 文档不存在或已删除，忽略索引事件 - documentId={}", documentId);
            return;
        }

        try {
            documentService.indexDocument(userId, documentId);
            log.info("[KB][INDEX_EVENT] 文档自动索引完成 - documentId={}, userId={}", documentId, userId);
        } catch (Exception e) {
            log.error("[KB][INDEX_EVENT] 文档自动索引失败 - documentId={}, userId={}", documentId, userId, e);
            failureHandler.handleFailure(document, KbProcessStage.INDEX, e);
            throw e instanceof BusinessException ? (BusinessException) e : new RuntimeException(e);
        }
    }
}
