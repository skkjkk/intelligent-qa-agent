package com.jujiu.agent.mq;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.event.DocumentIndexEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 文档索引事件生产者。
 *
 * <p>用于在文档完成解析与分块后发送索引事件，
 * 驱动后续向量化与 Elasticsearch 索引流程。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/12
 */
@Component
@Slf4j
public class DocumentIndexProducer {


    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    
    public DocumentIndexProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                 KnowledgeBaseProperties knowledgeBaseProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
    }

    /**
     * 发送文档索引事件。
     *
     * @param event 文档索引事件
     */
    public void send(DocumentIndexEvent event) {
        if (event == null || event.getDocumentId() == null) {
            throw new BusinessException(ResultCode.KAFKA_SEND_FAILED, "文档索引事件不能为空");
        }

        String topic = knowledgeBaseProperties.getKafka().getTopicDocumentIndex();
        
        try {
            kafkaTemplate.send(topic, String.valueOf(event.getDocumentId()), event);
            log.info("[KB][INDEX_EVENT] 文档索引事件发送成功 - topic={}, documentId={}, userId={}",
                    topic, event.getDocumentId(), event.getUserId());
        } catch (Exception e) {
            log.error("[KB][INDEX_EVENT] 文档索引事件发送失败 - topic={}, documentId={}",
                    topic, event.getDocumentId(), e);
            throw new BusinessException(ResultCode.KAFKA_SEND_FAILED, "文档索引事件发送失败");
        }
    }
}
