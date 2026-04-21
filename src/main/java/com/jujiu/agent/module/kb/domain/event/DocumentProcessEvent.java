package com.jujiu.agent.module.kb.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档处理事件。
 *
 * <p>文档上传成功后通过 Kafka 发送此事件，由 {@code DocumentProcessConsumer}
 * 异步消费，触发后续的文本解析、分块、向量化与索引流程。
 *
 * <p>事件体保持轻量，仅携带文档标识与上下文信息，具体业务数据通过
 * {@code documentId} 查询数据库获取。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 9:57
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /** 待处理文档 ID。 */
    private Long documentId;

    /** 上传用户 ID，用于失败追溯与权限校验。 */
    private Long userId;

    /** 事件产生时间。 */
    private LocalDateTime timestamp;

    /** 当前重试次数，首次发送时为 0。 */
    private Integer retryCount;
}
