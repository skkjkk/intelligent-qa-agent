package com.jujiu.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.common.constant.BusinessConstants;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.entity.Message;
import com.jujiu.agent.model.entity.Session;
import com.jujiu.agent.repository.MessageRepository;
import com.jujiu.agent.repository.SessionRepository;
import com.jujiu.agent.service.ChatPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/30 21:04
 */
@Service
@Slf4j
public class ChatPersistenceServiceImpl implements ChatPersistenceService {


    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public ChatPersistenceServiceImpl(MessageRepository messageRepository,
                                      SessionRepository sessionRepository,
                                      ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成消息ID：message_ + 雪花ID
     */
    private String generateMessageId(){
        return "message_" + IdUtil.getSnowflakeNextIdStr();
    }
    
    @Override
    public Message saveUserMessage(String sessionId, String content) {
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(sessionId)
                .role(BusinessConstants.ROLE_USER)
                .content(content)
                .createdAt(LocalDateTime.now())
                .tokens(0)
                .build();
        messageRepository.insert(message);
        return message;
    }

    @Override
    public Message saveAssistantMessage(String sessionId, String content, Integer tokens) {
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(sessionId)
                .role(BusinessConstants.ROLE_ASSISTANT)
                .content(content)
                .createdAt(LocalDateTime.now())
                .tokens(tokens)
                .build();
        messageRepository.insert(message);
        return message;
    }

    @Override
    public void saveIntermediateMessages(String sessionId, List<DeepSeekMessage> messagesToSave) {
        if (messagesToSave == null || messagesToSave.isEmpty()) {
            return;
        }

        for (DeepSeekMessage message : messagesToSave) {
            try {
                if (message.getToolCalls() != null && !message.getToolCalls().isEmpty())
                {
                    Message toolCallMessage = Message.builder()
                            .messageId(generateMessageId())
                            .sessionId(sessionId)
                            .role(message.getRole().getValue())
                            .content(message.getContent())
                            .toolCalls(objectMapper.writeValueAsString(message.getToolCalls()))
                            .createdAt(LocalDateTime.now())
                            .tokens(0)
                            .build();
                    messageRepository.insert(toolCallMessage);
                    continue;
                }

                if (message.getToolCallId() != null) {
                    Message toolMessage = Message.builder()
                            .messageId(generateMessageId())
                            .sessionId(sessionId)
                            .role(message.getRole().getValue())
                            .content(message.getContent())
                            .toolCallId(message.getToolCallId())
                            .createdAt(LocalDateTime.now())
                            .tokens(0)
                            .build();
                    messageRepository.insert(toolMessage);
                }
            } catch (Exception e) {
                throw new RuntimeException("保存中间消息失败", e);
            }
        }
    }

    @Override
    public void updateSessionAfterReply(Session session, String finalReply, int messageIncrement) {
        session.setLastMessage(finalReply.substring(0,
                Math.min(finalReply.length(),
                        BusinessConstants.MAX_LAST_MESSAGE_PREVIEW)) + "...");
        session.setMessageCount(session.getMessageCount() + messageIncrement);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.updateById(session);
    }

    @Override
    public void deleteSessionMessages(String sessionId) {
        messageRepository.deleteByMap(java.util.Map.of("session_id", sessionId));
    }

    @Override
    public void updateSessionTitle(Session session, String title) {
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.updateById(session);
    }
}
