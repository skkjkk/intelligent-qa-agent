package com.jujiu.agent.service;


/**
 * @author 17644
 */
public interface ChatRateLimitService {
    void checkChatRateLimit(Long userId);
}
