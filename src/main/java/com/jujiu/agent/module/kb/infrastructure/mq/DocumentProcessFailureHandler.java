package com.jujiu.agent.module.kb.infrastructure.mq;

import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.domain.entity.KbDocumentProcessLog;
import com.jujiu.agent.module.kb.domain.enums.KbDocumentStatus;
import com.jujiu.agent.module.kb.domain.enums.KbProcessStage;
import com.jujiu.agent.module.kb.domain.enums.KbProcessStatus;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentProcessLogMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 *
 * 文档处理失败处理器。
 *
 * <p>在独立事务中记录失败状态，避免被 Kafka Consumer 的主事务回滚覆盖。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 16:47
 */

@Slf4j
@Component
public class DocumentProcessFailureHandler {

    private final KbDocumentMapper documentRepository;
    private final KbDocumentProcessLogMapper processLogRepository;

    public DocumentProcessFailureHandler(KbDocumentMapper documentRepository,
                                         KbDocumentProcessLogMapper processLogRepository) {
        this.documentRepository = documentRepository;
        this.processLogRepository = processLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(KbDocument document, KbProcessStage stage, Exception e) {
        log.error("文档处理失败, documentId={}, stage={}, message={}", document.getId(), stage, e.getMessage(), e);
        // 设置文档状态为失败
        document.setStatus(KbDocumentStatus.FAILED.name());
        
        // 如果是解析或分片阶段失败，则设置解析状态为失败
        if (stage == KbProcessStage.PARSE || stage == KbProcessStage.CHUNK) {
            document.setParseStatus(KbProcessStatus.FAILED.name());
        }
        // 如果是嵌入或索引阶段失败，则设置索引状态为失败
        if (stage == KbProcessStage.EMBEDDING || stage == KbProcessStage.INDEX) {
            document.setIndexStatus(KbProcessStatus.FAILED.name());
        }

        // 更新文档的更新时间
        document.setUpdatedAt(LocalDateTime.now());
        // 更新文档状态
        documentRepository.updateById(document);

        // 记录文档处理失败日志
        KbDocumentProcessLog logEntry = KbDocumentProcessLog.builder()
                .documentId(document.getId())
                .stage(stage.name())
                .status(KbProcessStatus.FAILED.name())
                .message(e.getMessage())
                .retryCount(0)
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        processLogRepository.insert(logEntry);

        log.info("已记录文档处理失败状态, documentId={}", document.getId());
    }
}
