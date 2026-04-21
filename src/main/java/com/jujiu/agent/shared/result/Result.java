package com.jujiu.agent.shared.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用响应结果封装类
 * <p>
 * 用于统一封装 API 接口的响应数据，包含状态码、消息、数据和响应时间戳。
 * </p>
 *
 * @param <T> 响应数据的类型
 * @author 居九
 * @version 1.0.0
 * @since 2026-03-20
 */
@Data
public class Result<T> {
    /**
     * HTTP 状态码
     */
    private Integer code; 
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 私有构造方法，只能通过静态方法创建
     */
    private Result() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功的响应结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }
    
    /**
     * 成功响应（自定义消息）
     *
     * @param data 响应数据
     * @param message 响应消息
     * @param <T> 数据类型
     * @return 成功的响应结果
     */
    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
    
    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 成功的响应结果
     */
    public static <T> Result<T> success() {
        return success(null);
    }
    
    /**
     * 失败响应（自定义状态码和消息）
     *
     * @param code 错误状态码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败的响应结果
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
    
    /**
     * 失败响应（根据 ResultCode）
     *
     * @param resultCode 响应码枚举
     * @param <T> 数据类型
     * @return 失败的响应结果
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(resultCode.getMessage());
        return result;
    }
}
