package com.jujiu.agent.module.chat.application.service;


/**
 * @author 17644
 */
public interface ChatRateLimitService {
    void checkChatRateLimit(Long userId);
}
