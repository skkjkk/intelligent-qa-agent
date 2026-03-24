package com.jujiu.agent.common.constant;

/**
 * Redis Key 常量类
 *
 * 统一管理所有 Redis Key 的前缀，便于维护和避免硬编码
 *
 * @author 居九
 * @since 2026-03-24
 */
public class RedisKeys {

    private RedisKeys() {
        // 工具类，禁止实例化
    }

    // ==================== 认证相关 ====================

    /**
     * 登录失败次数计数
     * Key格式: login:fail:{username}
     * TTL: 10分钟
     * 说明: 5次后锁定
     */
    public static final String LOGIN_FAIL = "login:fail:";

    /**
     * Token 黑名单（单Token失效）
     * Key格式: token:blacklist:{token}
     * TTL: Token剩余有效期
     * 说明: 用户登出时，当前Token加入黑名单
     */
    public static final String TOKEN_BLACKLIST = "token:blacklist:";

    /**
     * 用户级Token失效标记（修改密码时使用）
     * Key格式: user:logout:{userId}
     * TTL: 7天
     * 说明: 修改密码时，使该用户所有Token失效
     */
    public static final String USER_LOGOUT = "user:logout:";

    // ==================== 对话相关 ====================

    /**
     * 消息发送限流计数
     * Key格式: chat:rate:{userId}
     * TTL: 60秒
     * 说明: 每用户每分钟最多10条消息
     */
    public static final String CHAT_RATE = "chat:rate:";

    /**
     * 会话缓存
     * Key格式: session:{sessionId}
     * TTL: 30分钟
     */
    public static final String SESSION_CACHE = "session:";

    /**
     * 对话历史缓存
     * Key格式: conversation:{sessionId}
     * TTL: 24小时
     */
    public static final String CONVERSATION_HISTORY = "conversation:";

    // ==================== 工具方法 ====================

    /**
     * 获取登录失败计数 Key
     *
     * @param username 用户名
     * @return Redis Key
     */
    public static String getLoginFailKey(String username) {
        return LOGIN_FAIL + username;
    }

    /**
     * 获取 Token 黑名单 Key
     *
     * @param token JWT Token
     * @return Redis Key
     */
    public static String getTokenBlacklistKey(String token) {
        return TOKEN_BLACKLIST + token;
    }

    /**
     * 获取用户级失效 Key
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String getUserLogoutKey(Long userId) {
        return USER_LOGOUT + userId;
    }

    /**
     * 获取消息限流 Key
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String getChatRateKey(Long userId) {
        return CHAT_RATE + userId;
    }

    /**
     * 获取会话缓存 Key
     *
     * @param sessionId 会话ID
     * @return Redis Key
     */
    public static String getSessionCacheKey(String sessionId) {
        return SESSION_CACHE + sessionId;
    }

    /**
     * 获取对话历史 Key
     *
     * @param sessionId 会话ID
     * @return Redis Key
     */
    public static String getConversationHistoryKey(String sessionId) {
        return CONVERSATION_HISTORY + sessionId;
    }
}
