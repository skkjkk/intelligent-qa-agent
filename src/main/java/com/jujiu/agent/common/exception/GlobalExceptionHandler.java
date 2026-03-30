package com.jujiu.agent.common.exception;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.rmi.AccessException;

/**
 * 全局异常处理器
 * <p>
 * 统一处理系统中抛出的各类异常，返回标准化的错误响应
 * 使用 @RestControllerAdvice 注解实现全局异常捕获
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 20:16
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数验证异常（@Valid 校验失败）
     *
     * @param e MethodArgumentNotValidException 参数验证异常对象
     * @return 统一的错误响应结果（400 状态码 + 具体错误信息）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 获取错误信息
        FieldError defaultMessage = e.getBindingResult().getFieldError();
        String message = defaultMessage != null ? defaultMessage.getDefaultMessage() : "参数错误";
        log.warn("[EXCEPTION][VALIDATION] 参数验证失败 - field={}, reason={}, errorCode=400", 
                defaultMessage != null ? defaultMessage.getField() : "unknown", message);
        return Result.fail(400, message);
    }

    /**
     * 处理业务异常
     *
     * @param e BusinessException 业务逻辑中抛出的异常对象
     * @return 统一的错误响应结果（包含错误码和错误消息）
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.error("[EXCEPTION][SYSTEM] 系统异常 - type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.fail(e.getResultCode());
    }

    /**
     * 处理其他未知异常（兜底处理）
     *
     * @param e Exception 系统运行时异常对象
     * @return 统一的错误响应结果（500 状态码 + 通用错误消息）
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("[EXCEPTION][SYSTEM] 系统异常 - type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }

    /**
     * 处理访问异常
     *
     * @param e AccessException 访问异常对象
     * @return 统一的错误响应结果（403 状态码 + 通用错误消息）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result handleAccessException(AccessDeniedException e) {
        log.warn("[EXCEPTION][ACCESS_DENIED] 访问被拒绝 - message={}, errorCode=403", e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }
    
    /**
     * 处理请求方法不支持异常
     *
     * @param e HttpRequestMethodNotSupportedException 请求方法不支持异常对象
     * @return 统一的错误响应结果（405 状态码 + 通用错误消息）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("[EXCEPTION][METHOD_NOT_ALLOWED] 请求方法不支持 - method={}, message={}, errorCode=405", 
                e.getMethod() != null ? e.getMethod() : "unknown", e.getMessage());
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED);
    }
    
}
