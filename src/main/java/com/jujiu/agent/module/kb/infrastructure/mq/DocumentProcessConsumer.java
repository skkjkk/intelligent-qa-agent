package com.jujiu.agent.module.kb.infrastructure.mq;

import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.domain.entity.KbDocument;
import com.jujiu.agent.module.kb.domain.entity.KbDocumentProcessLog;
import com.jujiu.agent.module.kb.domain.enums.KbDocumentStatus;
import com.jujiu.agent.module.kb.domain.enums.KbProcessStage;
import com.jujiu.agent.module.kb.domain.enums.KbProcessStatus;
import com.jujiu.agent.module.kb.domain.event.DocumentIndexEvent;
import com.jujiu.agent.module.kb.domain.event.DocumentProcessEvent;
import com.jujiu.agent.module.kb.infrastructure.parser.DocumentParser;
import com.jujiu.agent.module.kb.infrastructure.parser.DocumentParserFactory;
import com.jujiu.agent.module.kb.infrastructure.parser.TikaDocumentParser;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentProcessLogMapper;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbDocumentMapper;
import com.jujiu.agent.module.kb.application.service.ChunkService;
import com.jujiu.agent.module.kb.infrastructure.storage.MinioFileService;
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
    private final KbDocumentMapper documentRepository;
    private final KbDocumentProcessLogMapper documentProcessLogRepository;
    private final MinioFileService minioFileService;    
    private final TikaDocumentParser tikaDocumentParser;
    private final DocumentParserFactory parserFactory;
    private final ChunkService chunkService;
    private final DocumentProcessFailureHandler failureHandler;
    private final DocumentIndexProducer documentIndexProducer;

    public DocumentProcessConsumer(KbDocumentMapper documentRepository,
                                   KbDocumentProcessLogMapper documentProcessLogRepository,
                                   TikaDocumentParser tikaDocumentParser,
                                   DocumentParserFactory parserFactory,
                                   MinioFileService minioFileService,
                                   DocumentProcessFailureHandler failureHandler,
                                   ChunkService chunkService,
                                   DocumentIndexProducer documentIndexProducer) {
        this.documentRepository = documentRepository;
        this.documentProcessLogRepository = documentProcessLogRepository;
        this.tikaDocumentParser = tikaDocumentParser;
        this.parserFactory = parserFactory;
        this.minioFileService = minioFileService;
        this.failureHandler = failureHandler;
        this.chunkService = chunkService;
        this.documentIndexProducer = documentIndexProducer;
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
            String normalizedText = text == null ? "" : text.trim();
            if (normalizedText.isEmpty()) {
                throw new BusinessException(ResultCode.FILE_READ_ERROR, "文档解析结果为空");
            }

            if (normalizedText.length() < 10) {
                log.warn("[KB][PARSER] 文档文本较短，可能影响检索质量 - documentId={}, fileName={}, textLength={}",
                        document.getId(), document.getFileName(), normalizedText.length());
            }


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

            DocumentIndexEvent indexEvent = buildIndexEvent(documentId, document.getOwnerUserId());
            documentIndexProducer.send(indexEvent);

            log.info("文档分块完成并已发送索引事件，documentId={}, chunkCount={}", documentId, chunkCount);

        } catch (Exception e) {
            log.error("文档分块失败，documentId={}", documentId, e);
            failureHandler.handleFailure(document, KbProcessStage.CHUNK, e);
            throw new RuntimeException("文档分块失败", e);
        }
    }
    
    /**
     * 构建文档索引事件。
     *
     * @param documentId 文档 ID
     * @param userId 用户 ID
     * @return 文档索引事件
     */
    private DocumentIndexEvent buildIndexEvent(Long documentId, Long userId) {
        return DocumentIndexEvent.builder()
                .documentId(documentId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    /**
     * 解析文档为纯文本。
     *
     * <p>当前版本采用：
     * <ul>
     *     <li>按文件类型优先使用专用解析器</li>
     *     <li>专用解析器失败时自动降级到 Tika 通用解析器</li>
     * </ul>
     *
     * @param document 文档记录
     * @return 纯文本内容
     */
    private String parseDocument(KbDocument document) {
        // 1. 记录 PARSE PROCESSING 日志
        insertProcessLog(document.getId(), KbProcessStage.PARSE, KbProcessStatus.PROCESSING, "开始解析");

        // 2. 从 MinIO 下载并优先使用专用解析器
        try (InputStream inputStream = minioFileService.getObjectStream(document.getFilePath())) {
            DocumentParser parser = parserFactory.getParser(document.getFileType());

            log.info("[KB][PARSER] 使用主解析器解析文档 - documentId={}, fileName={}, fileType={}, parser={}",
                    document.getId(), document.getFileName(), document.getFileType(), parser.getClass().getSimpleName());

            String text;
            try {
                text = parser.parse(inputStream, document.getFileName());
            } catch (Exception parserException) {
                log.warn("[KB][PARSER] 主解析器失败，尝试使用 Tika 兜底 - documentId={}, fileName={}, fileType={}, parser={}",
                        document.getId(), document.getFileName(), document.getFileType(), parser.getClass().getSimpleName(), parserException);

                // 重新打开输入流，避免主解析器已消费流内容
                try (InputStream fallbackInputStream = minioFileService.getObjectStream(document.getFilePath())) {
                    text = tikaDocumentParser.parse(fallbackInputStream, document.getFileName());
                    log.info("[KB][PARSER] Tika 兜底解析成功 - documentId={}, fileName={}, fileType={}",
                            document.getId(), document.getFileName(), document.getFileType());
                }
            }

            String normalizedText = text == null ? "" : text.trim();
            if (normalizedText.isEmpty()) {
                log.warn("[KB][PARSER] 文档解析结果为空 - documentId={}, fileName={}",
                        document.getId(), document.getFileName());
                insertProcessLog(document.getId(), KbProcessStage.PARSE, KbProcessStatus.FAILED, "文档解析结果为空");
                throw new BusinessException(ResultCode.FILE_READ_ERROR, "文档解析结果为空");
            }

            if (normalizedText.length() < 10) {
                log.warn("[KB][PARSER] 文档文本较短，可能影响检索质量 - documentId={}, fileName={}, textLength={}",
                        document.getId(), document.getFileName(), normalizedText.length());
            }

            // 4. 记录 PARSE SUCCESS 日志
            insertProcessLog(document.getId(), KbProcessStage.PARSE, KbProcessStatus.SUCCESS, "解析完成");

            log.info("[KB][PARSER] 文档解析成功 - documentId={}, fileName={}, textLength={}",
                    document.getId(), document.getFileName(), normalizedText.length());

            return normalizedText;
        } catch (Exception e) {
            log.error("[KB][PARSER] 文档解析失败 - documentId={}, fileName={}",
                    document.getId(), document.getFileName(), e);

            // 5. 记录 PARSE FAILED 日志
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
