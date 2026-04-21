package com.jujiu.agent.shared.result;

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

    
    /***********************业务错误码************************/
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
     * 无效的页大小
     */
    INVALID_PAGE_SIZE(1010, "无效的页大小"),
    /**
     * 无效的页码
     */
    INVALID_PAGE_NUMBER(1011, "无效的页码"),

    /**
     * 会话不存在或无权限
     */
    SESSION_NOT_FOUND(2001, "会话不存在或无权限"),

    /**
     * 无效的参数
     */
    INVALID_PARAMETER(1012, "无效的参数"),
    
    /***********************DeepSeek相关错误码************************/
    /**
     * DeepSeek API 返回空结果
     */
    DEEPSEEK_API_RETURN_NULL(3001, "DeepSeek API 返回空结果"),

    /**
     * DeepSeek API 响应消息格式异常
     */
    DEEPSEEK_API_RETURN_FORMAT_ERROR(3002, "DeepSeek API 返回的消息格式异常"),

    /**
     * 函数调用最大次数 exceeded
     */
    FUNCTION_CALLING_MAX_ITERATIONS(3005, "函数调用最大次数 exceeded"),
    /**
     * 发送消息过于频繁，请稍后再试
     */
    CHAT_RATE_LIMIT_EXCEEDED(3003, "发送消息过于频繁，请稍后再试"),
    /**
     * 限流配置非法
     */
    RATE_LIMIT_CONFIG_INVALID(3004, "限流配置非法"),

    
    
    /***********************文档相关错误码************************/
    /**
     * MinIO 文件获取失败
     */
    MINIO_FILE_GET_FAILED(4001, "MinIO 文件获取失败"),

    /**
     * MinIO 文件删除失败
     */
    MINIO_FILE_DELETE_FAILED(4002, "MinIO 文件删除失败"),
    
    /**
     * MinIO 文件上传失败
     */
    MINIO_FILE_UPLOAD_FAILED(4003, "MinIO 文件上传失败"),

    /**
     * MinIO 桶初始化失败
     */
    MINIO_BUCKET_INIT_FAILED(4004, "MinIO 桶初始化失败"),

    /**
     * 不支持的文件类型
     */
    UNSUPPORTED_DOCUMENT_TYPE(4005, "不支持的文档类型"),
    
    /**
     * 文档解析失败
     */
    DOCUMENT_PARSE_ERROR(4006, "文档解析失败"),

    /**
     * 文件哈希值错误
     */
    FILE_HASH_ERROR(4007, "文件哈希值错误"),
    
    /**
     * 文档重复
     */
    DOCUMENT_DUPLICATE(4008, "文档重复"),

    /**
     * 文档不存在
     */
    DOCUMENT_NOT_FOUND(4009, "文档不存在"),
    
    /**
     * 文件读取错误
     */
    FILE_READ_ERROR(4010, "文件读取错误"),

    /**
     * Kafka 发送消息失败
     */
    KAFKA_SEND_FAILED(4011, "Kafka 发送消息失败"),

    /**
     * 服务尚未实现
     */
    SYSTEM_ERROR(4012, "服务尚未实现"),

    /**
     * Embedding 配置缺失
     */
    EMBEDDING_CONFIG_MISSING(4013, "Embedding 配置缺失"),
    /**
     * Embedding 配置非法
     */
    EMBEDDING_CONFIG_INVALID(4014, "Embedding 配置非法"),
    /**
     * Embedding 远端调用超时
     */
    EMBEDDING_REMOTE_TIMEOUT(4015, "Embedding 远端调用超时"),
    /**
     * Embedding 远端服务异常
     */
    EMBEDDING_REMOTE_ERROR(4016, "Embedding 远端服务异常"),
    /**
     * Embedding 服务限流
     */
    EMBEDDING_REMOTE_RATE_LIMITED(4017, "Embedding 服务限流"),
    /**
     * Embedding 响应为空
     */
    EMBEDDING_RESPONSE_EMPTY(4018, "Embedding 响应为空"),
    /**
     * Embedding 响应格式异常
     */
    EMBEDDING_RESPONSE_FORMAT_ERROR(4019, "Embedding 响应格式异常"),
    /**
     * Embedding 缓存读取失败
     */
    EMBEDDING_CACHE_READ_ERROR(4020, "Embedding 缓存读取失败"),
    /**
     * Embedding 缓存写入失败
     */
    EMBEDDING_CACHE_WRITE_ERROR(4021, "Embedding 缓存写入失败"),
    /**
     * Embedding 向量维度异常
     */
    EMBEDDING_DIMENSION_MISMATCH(4022, "Embedding 向量维度异常"),

    /**
     * 索引重建校验失败
     */
    INDEX_REBUILD_VERIFY_FAILED(4023, "索引重建校验失败"),
    /**
     * 索引诊断失败
     */
    INDEX_DIAGNOSIS_FAILED(4024, "索引诊断失败"),
    /**
     * 索引状态不一致
     */
    INDEX_STATE_INCONSISTENT(4025, "索引状态不一致"),
    /**
     * 索引修复失败
     */
    INDEX_REPAIR_FAILED(4026, "索引修复失败"),
    /**
     * ES 索引删除失败
     */
    ES_INDEX_DELETE_FAILED(4027, "ES索引删除失败"),
    /**
     * ES 索引计数失败
     */
    ES_INDEX_COUNT_FAILED(4028, "ES索引计数失败"),
    
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
