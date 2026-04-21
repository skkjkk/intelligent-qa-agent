package com.jujiu.agent.shared.constant;

/**
 * 业务常量类
 *
 * 统一管理业务相关的常量，包括时间限制、次数限制等
 *
 * @author 居九
 * @since 2026-03-24
 */
public class BusinessConstants {

    private BusinessConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 时间限制（毫秒） ====================

    /**
     * 登录失败锁定时间:10分钟
     */
    public static final long LOGIN_FAIL_LOCK_TIME = 10 * 60 * 1000L; 

    /**
     * 消息限流时间窗口:
     */
    public static final long CHAT_RATE_WINDOW = 60 * 1000L;

    /**
     * 用户级Token失效时间:7天
     */
    public static final long USER_LOGOUT_EXPIRE = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 会话缓存 TTL:30分钟
     */
    public static final long SESSION_CACHE_TTL = 30 * 60 * 1000L;

    /**
     * 对话历史缓存 TTL:24小时  
     */
    public static final long CONVERSATION_HISTORY_TTL = 24 * 60 * 60 * 1000L; 

    // ==================== 次数限制 ====================

    /**
     * 登录失败最大次数
     */
    public static final int LOGIN_FAIL_MAX_COUNT = 5;
    

    // ==================== 消息限制 ====================

    /**
     * 消息最大长度（字符）
     */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    /**
     * 会话标题最大长度
     */
    public static final int MAX_TITLE_LENGTH = 200;

    /**
     * 最后消息预览最大长度
     */
    public static final int MAX_LAST_MESSAGE_PREVIEW = 100;

    /**
     * 函数调用最大迭代次数
     */
    public static final int FUNCTION_CALLING_MAX_ITERATIONS = 5;
    
    // ==================== 分页默认值 ====================

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 默认每页数量
     */
    public static final int DEFAULT_SIZE = 10;

    /**
     * 最大每页数量（防止一次查询过多）
     */
    public static final int MAX_SIZE = 100;

    // ==================== API 路径 ====================

    /**
     * API 版本前缀
     */
    public static final String API_V1 = "/api/v1";

    /**
     * 认证模块路径
     */
    public static final String AUTH_PATH = API_V1 + "/auth";

    /**
     * 对话模块路径
     */
    public static final String CHAT_PATH = API_V1 + "/chat";

    // ==================== JWT 相关 ====================

    /**
     * JWT 密钥前缀
     */
    public static final String JWT_SECRET_PREFIX = "JWT_SECRET:";

    /**
     * 默认角色
     */
    public static final String DEFAULT_ROLE = "USER";

    /**
     * 管理员角色
     */
    public static final String ADMIN_ROLE = "ADMIN";

    // ==================== 用户状态 ====================

    /**
     * 用户正常状态
     */
    public static final int USER_STATUS_NORMAL = 1;

    /**
     * 用户禁用状态
     */
    public static final int USER_STATUS_DISABLED = 0;

    // ==================== 会话状态 ====================

    /**
     * 会话活跃状态
     */
    public static final int SESSION_STATUS_ACTIVE = 1;

    /**
     * 会话已关闭状态
     */
    public static final int SESSION_STATUS_CLOSED = 0;

    // ==================== 消息角色 ====================

    /**
     * 用户消息角色
     */
    public static final String ROLE_USER = "user";

    /**
     * AI助手消息角色
     */
    public static final String ROLE_ASSISTANT = "assistant";

    /**
     * 系统消息角色
     */
    public static final String ROLE_SYSTEM = "system";

    // ==================== SSE 相关 ====================

    /**
     * SSE 流式响应超时时间（毫秒）
     */
    public static final long SSE_TIMEOUT = 3 * 60 * 1000L; // 3分钟

    /**
     * SSE 缓冲区大小
     */
    public static final int SSE_BUFFER_SIZE = 20;

    /**
     * SSE 流结束标记
     */
    public static final String SSE_DONE = "[DONE]";
}
