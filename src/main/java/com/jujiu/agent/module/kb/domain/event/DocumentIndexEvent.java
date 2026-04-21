package com.jujiu.agent.module.kb.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档索引事件。
 *
 * <p>用于在文档完成解析与分块后，驱动后续向量化与 Elasticsearch 索引流程。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIndexEvent {
    /**
     * 文档 ID。
     */
    private Long documentId;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 事件时间。
     */
    private LocalDateTime timestamp;

    /**
     * 当前重试次数。
     */
    private Integer retryCount;
}
