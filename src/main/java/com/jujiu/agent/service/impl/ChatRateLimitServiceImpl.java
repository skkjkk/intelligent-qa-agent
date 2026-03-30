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
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/30 20:59
 */
@Service
@Slf4j
public class ChatRateLimitServiceImpl implements ChatRateLimitService {
    private final StringRedisTemplate redisTemplate;
    private final DeepSeekProperties deepSeekProperties;

    public ChatRateLimitServiceImpl(StringRedisTemplate redisTemplate, DeepSeekProperties
            deepSeekProperties) {
        this.redisTemplate = redisTemplate;
        this.deepSeekProperties = deepSeekProperties;
    }

    @Override
    public void checkChatRateLimit(Long userId) {
        String key = RedisKeys.getChatRateKey(userId);
        Long count = redisTemplate.opsForValue().increment(key);

        int maxMessagesPerMinute = deepSeekProperties.getMaxMessagesPerMinute();
        int rateLimitWindowSeconds = deepSeekProperties.getRateLimitWindowSeconds();

        if (count != null && count == 1) {
            redisTemplate.expire(key, rateLimitWindowSeconds, TimeUnit.SECONDS);
        }

        if (count != null && count > maxMessagesPerMinute) {
            log.warn("[CHAT][RATE_LIMIT] 用户消息发送过于频繁 - userId={}, count={}, limit={}, windowSeconds={}",
                    userId, count, maxMessagesPerMinute, rateLimitWindowSeconds);
            throw new BusinessException(ResultCode.CHAT_RATE_LIMIT_EXCEEDED);
        }
    }
}

