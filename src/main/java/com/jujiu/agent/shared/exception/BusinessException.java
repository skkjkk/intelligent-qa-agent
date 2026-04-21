package com.jujiu.agent.shared.exception;

import com.jujiu.agent.shared.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常类
 * 
 * 用于封装业务逻辑中的错误信息，包含错误码和错误消息
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 17:56
 */
@Getter
public class BusinessException extends RuntimeException {
    
    /**
     * 错误码和错误消息
     */
    private final ResultCode resultCode;
    
    /**
     * 构造方法
     * 
     * @param resultCode 错误码（包含错误状态码和消息）
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }
    
    /**
     * 构造方法（带自定义消息）
     * 
     * @param resultCode 错误码
     * @param message 自定义错误消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
