package com.jujiu.agent.module.chat.application.service;

import com.jujiu.agent.module.chat.domain.entity.Message;
import com.jujiu.agent.module.chat.domain.entity.Session;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmMessage;

import java.util.List;

/**
 * @author 17644
 */
public interface ChatPersistenceService {
    
    Message saveUserMessage(String sessionId, String content);
    
    Message saveAssistantMessage(String sessionId, String content, Integer tokens);
    
    void saveIntermediateMessages(String sessionId, List<LlmMessage> messagesToSave);

    void updateSessionAfterReply(Session session, String finalReply, int messageIncrement);

    void deleteSessionMessages(String sessionId);

    void updateSessionTitle(Session session, String title);
}
