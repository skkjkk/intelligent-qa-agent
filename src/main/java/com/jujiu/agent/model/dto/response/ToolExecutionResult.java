package com.jujiu.agent.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 9:36
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {
    // 是否成功
    private boolean success;

    // 消息
    private String message;

    // 数据
    private Object data;

    // 错误码
    private String errorCode;
    
    // 执行时间
    private Long durationMs;
}
