package com.jujiu.agent.module.kb.domain.enums;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/2 10:47
 */
public enum KbProcessStage {
    /**
     * 上传
     */
    UPLOAD,
    /**
     * 解析
     */
    PARSE,
    /**
     * 分块
     */
    CHUNK,
    /**
     * 嵌入向量
     */
    EMBEDDING,
    /**
     * 索引
     */
    INDEX,
}
