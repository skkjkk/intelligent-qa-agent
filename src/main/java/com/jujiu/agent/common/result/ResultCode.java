package com.jujiu.agent.common.result;

import lombok.Getter;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 15:36
 */

/**
 * 统一响应结果状态码枚举
 * <p>
 * 定义了系统中所有可能的业务状态码，用于统一 API 响应格式
 * </p>
 *
 * @author 居九
 * @since 2026-03-19
 */
@Getter
public enum ResultCode {
    /**
     * 请求成功
     */
    SUCCESS(200, "成功"),

    /**
     * 客户端错误 4xxx
     */
    BAD_REQUEST(400, "请求参数错误"),
    /**
     * 未登录或 Token 已过期
     */
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    /**
     * 没有权限
     */
    FORBIDDEN(403, "没有权限"),
    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),
    /**
     * 请求方法不支持
     */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /**
     * 业务错误 1xxx
     */
    LOGIN_FAILED(1001, "用户名或密码错误"),
    /**
     * 用户不存在
     */
    USER_NOT_FOUND(1002, "用户不存在"),
    /**
     * 用户已被禁用
     */
    USER_DISABLED(1003, "用户已被禁用"),
    /**
     * 用户名已存在
     */
    USERNAME_EXISTS(1004, "用户名已存在"),
    /**
     * 邮箱已存在
     */
    EMAIL_EXISTS(1005, "邮箱已存在"),
    /**
     * Token 无效
     */
    TOKEN_INVALID(1006, "Token 无效"),
    /**
     * Token 已过期
     */
    TOKEN_EXPIRED(1007, "Token 已过期"),
    /**
     * 登录失败次数过多，请10分钟后再试
     */
    LOGIN_TOO_MANY(1008, "登录失败次数过多，请10分钟后再试"),
    /**
     * 旧密码不正确
     */
    OLD_PASSWORD_WRONG(1009, "旧密码不正确"),

    /**
     * 会话不存在或无权限
     */
    SESSION_NOT_FOUND(2001, "会话不存在或无权限"),
    
    /**
     * 服务器内部错误
     */
    INTERNAL_ERROR(5000, "服务器内部错误"),
    ;

    /**
     * 状态码
     */
    private final Integer code;
    /**
     * 描述信息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param code    状态码
     * @param message 描述信息
     */
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
