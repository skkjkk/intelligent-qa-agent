package com.jujiu.agent.module.kb.infrastructure.mq;

import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.domain.event.DocumentProcessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;


/**
 * 文档处理事件生产者。
 *
 * <p>负责将 {@link DocumentProcessEvent} 发送到 Kafka，
 * 触发异步文档解析流水线。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 16:21
 */
@Component
@Slf4j
public class DocumentProcessProducer {

    private final KafkaTemplate<String, DocumentProcessEvent> kafkaTemplate;
    private final String topic;
    
    public DocumentProcessProducer(KafkaTemplate<String, DocumentProcessEvent> kafkaTemplate, KnowledgeBaseProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.getKafka().getTopicDocumentProcess();
    }

    /**
     * 发送文档处理事件到 Kafka
     * 将文档处理事件发送到指定的 Kafka 主题，触发异步文档解析流水线。
     * 使用同步发送方式等待 Kafka 确认，并记录发送结果的 offset 信息。
     *
     * @param event 文档处理事件，包含待处理的文档 ID 等信息
     * @throws BusinessException 当 Kafka 发送失败时抛出，包含错误码和错误信息
     */
    public void send(DocumentProcessEvent event) {
        try {
            // 同步发送消息到 Kafka 并等待确认
            SendResult<String, DocumentProcessEvent> result = kafkaTemplate
                            .send(topic, String.valueOf(event.getDocumentId()), event)
                            .get();
            log.info("发送文档处理事件成功，documentId={}, offset={}",
                    event.getDocumentId(), result.getRecordMetadata().offset());
                
        } catch (Exception e) {
            log.error("发送文档处理事件失败，documentId={}", event.getDocumentId(), e);
            // 抛出业务异常，由上层处理
            throw new BusinessException(ResultCode.KAFKA_SEND_FAILED, "发送文档处理事件失败");
        }
    }
}
