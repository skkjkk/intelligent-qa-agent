package com.jujiu.agent.service.impl;

import com.jujiu.agent.common.constant.RedisKeys;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.DeepSeekProperties;
import com.jujiu.agent.service.ChatRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 聊天限流服务实现类。
 * <p>基于 Redis 计数器对用户聊天请求进行频次限制，防止单个用户在短时间窗口内发送过多消息。</p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/30 20:59
 */
@Service
@Slf4j
public class ChatRateLimitServiceImpl implements ChatRateLimitService {
    /** Redis 字符串模板，用于限流计数器的读写。 */
    private final StringRedisTemplate redisTemplate;

    /** DeepSeek 相关配置属性，包含限流阈值与时间窗口配置。 */
    private final DeepSeekProperties deepSeekProperties;

    /**
     * 构造方法。
     *
     * @param redisTemplate       Redis 字符串模板
     * @param deepSeekProperties  DeepSeek 配置属性
     */
    public ChatRateLimitServiceImpl(StringRedisTemplate redisTemplate, DeepSeekProperties
            deepSeekProperties) {
        this.redisTemplate = redisTemplate;
        this.deepSeekProperties = deepSeekProperties;
    }

    /**
     * 检查指定用户是否超出聊天限流阈值。
     *
     * @param userId 用户 ID
     * @throws BusinessException 当请求次数超过配置阈值时抛出，错误码为 {@link ResultCode#CHAT_RATE_LIMIT_EXCEEDED}
     */
    @Override
    public void checkChatRateLimit(Long userId) {
        // 1. 构建限流 Redis Key
        String key = RedisKeys.getChatRateKey(userId);

        // 2. 对计数器执行自增操作
        Long count = redisTemplate.opsForValue().increment(key);

        int maxMessagesPerMinute = deepSeekProperties.getMaxMessagesPerMinute();
        int rateLimitWindowSeconds = deepSeekProperties.getRateLimitWindowSeconds();

        // 3. 首次自增时设置 Key 的过期时间
        if (count != null && count == 1) {
            redisTemplate.expire(key, rateLimitWindowSeconds, TimeUnit.SECONDS);
        }

        // 4. 判断是否超出最大请求次数限制
        if (count != null && count > maxMessagesPerMinute) {
            log.warn("[CHAT][RATE_LIMIT] 用户消息发送过于频繁 - userId={}, count={}, limit={}, windowSeconds={}",
                    userId, count, maxMessagesPerMinute, rateLimitWindowSeconds);
            throw new BusinessException(ResultCode.CHAT_RATE_LIMIT_EXCEEDED);
        }
    }
}

