package com.jujiu.agent.common.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 15:30
 */
@Data
public class Result<T> {
    // 状态码
    private Integer code; 
    
    // 状态信息
    private String message;
    
    // 数据
    private T data;

    // 时间戳
    private LocalDateTime timestamp;
    
    // 私有构造方法，只能通过静态方法创建
    private Result() {
        this.timestamp = LocalDateTime.now();
    }

    // 成功响应
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }
    
    // 成功响应（自定义消息）
    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
    
    // 成功响应（无数据）
    public static <T> Result<T> success() {
        return success(null);
    }
    
    // 失败响应（自定义 code 和 message）
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
    
    // 失败响应（根据 ResultCode）
    public static <T> Result<T> fail(ResultCode resultCode) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(resultCode.getMessage());
        return result;
    }
}
