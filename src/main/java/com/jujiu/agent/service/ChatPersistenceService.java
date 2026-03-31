package com.jujiu.agent.service;

import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.entity.Message;
import com.jujiu.agent.model.entity.Session;

import java.util.List;

/**
 * @author 17644
 */
public interface ChatPersistenceService {
    
    Message saveUserMessage(String sessionId, String content);
    
    Message saveAssistantMessage(String sessionId, String content, Integer tokens);
    
    void saveIntermediateMessages(String sessionId, List<DeepSeekMessage> messagesToSave);

    void updateSessionAfterReply(Session session, String finalReply, int messageIncrement);

    void deleteSessionMessages(String sessionId);

    void updateSessionTitle(Session session, String title);
}
