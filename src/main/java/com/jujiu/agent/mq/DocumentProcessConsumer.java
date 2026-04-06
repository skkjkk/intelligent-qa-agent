package com.jujiu.agent.mq;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.model.entity.KbDocument;
import com.jujiu.agent.model.entity.KbDocumentProcessLog;
import com.jujiu.agent.model.enums.KbDocumentStatus;
import com.jujiu.agent.model.enums.KbProcessStage;
import com.jujiu.agent.model.enums.KbProcessStatus;
import com.jujiu.agent.model.event.DocumentProcessEvent;
import com.jujiu.agent.parser.DocumentParser;
import com.jujiu.agent.parser.DocumentParserFactory;
import com.jujiu.agent.repository.KbDocumentProcessLogRepository;
import com.jujiu.agent.repository.KbDocumentRepository;
import com.jujiu.agent.service.kb.ChunkService;
import com.jujiu.agent.storage.MinioFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * 文档处理事件消费者。
 *
 * <p>订阅 Kafka 文档处理主题，执行完整的异步流水线：
 * <ol>
 *   <li>下载原始文件</li>
 *   <li>文本解析</li>
 *   <li>分块并保存</li>
 *   <li>更新文档状态与统计</li>
 * </ol>
 *
 * <p>任一阶段失败均会记录错误日志并抛出异常，由 Kafka 重试机制驱动补偿。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 16:25
 */
@Component
@Slf4j
public class DocumentProcessConsumer {
    private final KbDocumentRepository documentRepository;
    private final KbDocumentProcessLogRepository documentProcessLogRepository;
    private final MinioFileService minioFileService;    
    private final DocumentParserFactory parserFactory;
    private final ChunkService chunkService;
    private final DocumentProcessFailureHandler failureHandler;
    
    public DocumentProcessConsumer(KbDocumentRepository documentRepository,
                                   KbDocumentProcessLogRepository documentProcessLogRepository,
                                   DocumentParserFactory parserFactory,
                                   MinioFileService minioFileService,
                                   DocumentProcessFailureHandler failureHandler,
                                   ChunkService chunkService) {
        this.documentRepository = documentRepository;
        this.documentProcessLogRepository = documentProcessLogRepository;
        this.parserFactory = parserFactory;
        this.minioFileService = minioFileService;
        this.failureHandler = failureHandler;
        this.chunkService = chunkService;
    }

    /**
     * 消费文档处理事件
     * 监听 Kafka 文档处理主题，执行完整的异步处理流程：下载文件、解析文本、分块保存并更新状态。
     * 处理失败时会记录错误日志并抛出异常，由 Kafka 重试机制进行补偿处理。
     *
     * @param event 文档处理事件，包含待处理的文档 ID
     * @throws RuntimeException 当文档处理失败时抛出，触发 Kafka 重试机制
     */
    @Transactional
    @KafkaListener(topics = "${knowledge-base.kafka.topic-document-process}")
    public void consume(DocumentProcessEvent event) {
        log.info("收到文档处理事件，documentId={}", event.getDocumentId());

        // 获取文档记录
        Long documentId = event.getDocumentId();
        KbDocument document = documentRepository.selectById(documentId);

        // 验证文档是否存在且未被删除
        if (document == null || document.getDeleted().equals(1)) {
            log.warn("文档不存在或已删除，documentId={}", documentId);
            return;
        }
        
        String text;
        try {
            // 更新文档解析状态为 PROCESSING
            document.setParseStatus(KbProcessStatus.PROCESSING.name());
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.updateById(document);

            // 下载文件并解析为纯文本
            text = parseDocument(document);
            
            // 更新文档状态为成功，设置分块数量统计
            document.setParseStatus(KbProcessStatus.SUCCESS.name());
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.updateById(document);
        } catch (Exception e) {
            log.error("文档解析失败，documentId={}", documentId, e);
            failureHandler.handleFailure(document, KbProcessStage.PARSE, e);
            throw new RuntimeException("文档解析失败", e);
        }
        
        try {
            int chunkCount = chunkService.splitAndSave(documentId, text).size();

            document.setStatus(KbDocumentStatus.PROCESSING.name());
            document.setIndexStatus(KbProcessStatus.PENDING.name());
            document.setChunkCount(chunkCount);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.updateById(document);

            log.info("文档分块完成，documentId={}, chunkCount={}", documentId, chunkCount);
        } catch (Exception e) {
            log.error("文档分块失败，documentId={}", documentId, e);
            failureHandler.handleFailure(document, KbProcessStage.CHUNK, e);
            throw new RuntimeException("文档分块失败", e);
        }
    }

    /**
     * 解析文档为纯文本。
     *
     * @param document 文档记录
     * @return 纯文本内容
     */
    private String parseDocument(KbDocument document) {
        // 1. 记录 PARSE PROCESSING 日志
        insertProcessLog(document.getId(), KbProcessStage.PARSE, KbProcessStatus.PROCESSING, "开始解析");

        // 2. 从 MinIO 下载
        try (InputStream inputStream = minioFileService.getObjectStream(document.getFilePath())) {
            DocumentParser parser = parserFactory.getParser(document.getFileType());
            String text = parser.parse(inputStream, document.getFileName());

            // 3. 记录 PARSE SUCCESS 日志
            insertProcessLog(document.getId(), KbProcessStage.PARSE, KbProcessStatus.SUCCESS, "解析完成");
            return text;
        } catch (Exception e) {
            log.error("文档解析失败, documentId={}", document.getId(), e);
            insertProcessLog(document.getId(), KbProcessStage.PARSE, KbProcessStatus.FAILED, e.getMessage());
            throw e instanceof BusinessException ? (BusinessException) e : new RuntimeException(e);
        }
    }
    
    /**
     * 插入处理日志。
     */
    private void insertProcessLog(Long documentId, KbProcessStage stage, KbProcessStatus status, String message){
        KbDocumentProcessLog logEntry = KbDocumentProcessLog.builder()
                .documentId(documentId)
                .stage(stage.name())
                .status(status.name())
                .message(message)
                .retryCount(0)
                .startedAt(LocalDateTime.now())
                .endedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        documentProcessLogRepository.insert(logEntry);
    }
    
}
