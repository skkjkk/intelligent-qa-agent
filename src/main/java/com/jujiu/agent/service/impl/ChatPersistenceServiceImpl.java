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
 * 聊天记录持久化服务实现
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
    
    /**
     * 保存用户消息
     * 创建并保存用户发送的聊天消息到数据库。
     *
     * @param sessionId 会话 ID，标识消息所属的会话
     * @param content 消息内容，即用户发送的文本
     * @return Message 保存后的消息对象，包含完整的消息信息（如 messageId、createdAt 等）
     */
    @Override
    public Message saveUserMessage(String sessionId, String content) {
        // 构建用户消息对象
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(sessionId)
                .role(BusinessConstants.ROLE_USER)
                .content(content)
                .createdAt(LocalDateTime.now())
                .tokens(0)
                .build();
        // 保存到数据库
        messageRepository.insert(message);
        return message;
    }

    /**
     * 保存 AI 助手消息
     * 创建并保存 AI 助手的回复消息到数据库。
     *
     * @param sessionId 会话 ID，标识消息所属的会话
     * @param content AI 回复的消息内容
     * @param tokens 消耗的 token 数量，用于统计 API 调用成本
     * @return Message 保存后的消息对象，包含完整的消息信息（如 messageId、createdAt 等）
     */
    @Override
    public Message saveAssistantMessage(String sessionId, String content, Integer tokens) {
        // 构建 AI 助手消息对象
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(sessionId)
                .role(BusinessConstants.ROLE_ASSISTANT)
                .content(content)
                .createdAt(LocalDateTime.now())
                .tokens(tokens)
                .build();
        // 保存到数据库
        messageRepository.insert(message);
        return message;
    }

    /**
     * 保存中间消息
     * 保存 Function Calling 过程中产生的中间消息，包括带 tool_calls 的 assistant 消息和 tool 响应消息。
     * 这些消息用于记录 AI 调用工具的完整过程，便于后续追溯和分析。
     *
     * @param sessionId 会话 ID，标识消息所属的会话
     * @param messagesToSave 需要保存的消息列表，包含 Function Calling 过程中的各类消息
     */
    @Override
    public void saveIntermediateMessages(String sessionId, List<DeepSeekMessage> messagesToSave) {
        // 空值检查，如果消息列表为空则直接返回
        if (messagesToSave == null || messagesToSave.isEmpty()) {
            return;
        }

        // 遍历所有待保存的消息
        for (DeepSeekMessage message : messagesToSave) {
            try {
                // 保存带 tool_calls 的 assistant 消息（AI 发起的工具调用请求）
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

                // 保存 tool 响应消息（工具执行结果）
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

    /**
     * AI 回复后更新会话信息
     * 在 AI 完成回复后，更新会话的最后消息、消息数量和更新时间。
     *
     * @param session 会话对象，需要更新的会话
     * @param finalReply AI 的最终回复内容
     * @param messageIncrement 消息增量，本次对话增加的消息数量（通常为 2，表示一问一答）
     */
    @Override
    public void updateSessionAfterReply(Session session, String finalReply, int messageIncrement) {
        // 更新最后消息预览（截取指定长度并添加省略号）
        session.setLastMessage(finalReply.substring(0,
                Math.min(finalReply.length(),
                        BusinessConstants.MAX_LAST_MESSAGE_PREVIEW)) + "...");
        // 更新消息总数
        session.setMessageCount(session.getMessageCount() + messageIncrement);
        // 更新会话时间戳
        session.setUpdatedAt(LocalDateTime.now());
        // 保存到数据库
        sessionRepository.updateById(session);
    }

    /**
     * 删除会话的所有消息
     * 根据会话 ID 删除该会话关联的所有聊天消息记录。
     *
     * @param sessionId 会话 ID，需要删除消息的会话标识
     */
    @Override
    public void deleteSessionMessages(String sessionId) {
        messageRepository.deleteByMap(java.util.Map.of("session_id", sessionId));
    }

    /**
     * 更新会话标题
     * 设置会话的标题并更新时间戳，通常用于自动生成或用户自定义会话标题。
     *
     * @param session 会话对象，需要更新标题的会话
     * @param title 新的会话标题
     */
    @Override
    public void updateSessionTitle(Session session, String title) {
        // 设置会话标题
        session.setTitle(title);
        // 更新会话时间戳
        session.setUpdatedAt(LocalDateTime.now());
        // 保存到数据库
        sessionRepository.updateById(session);
    }
}
