package com.jujiu.agent.module.kb.domain.enums;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/2 10:46
 */
public enum KbDocumentStatus {
    // 处理中
    PROCESSING,
    
    // 处理成功
    SUCCESS,
    
    /**
     * 处理失败
     */
    FAILED,
    
    /**
     * 禁用
     */
    DISABLED
}
