package com.jujiu.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.DeepSeekProperties;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.request.CreateSessionRequest;
import com.jujiu.agent.model.dto.request.SendMessageRequest;
import com.jujiu.agent.model.dto.response.ChatResponse;
import com.jujiu.agent.model.dto.response.SessionDetailResponse;
import com.jujiu.agent.model.dto.response.SessionResponse;
import com.jujiu.agent.model.entity.Message;
import com.jujiu.agent.model.entity.Session;
import com.jujiu.agent.repository.MessageRepository;
import com.jujiu.agent.repository.SessionRepository;
import com.jujiu.agent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 14:35
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {
    
    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SessionRepository sessionRepository;
    
    @Autowired
    private DeepSeekClient deepSeekClient;
    
    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 生成会话ID：session_ + 雪花ID
     */
    private String generateSessionId(){
        return "session_" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 生成消息ID：message_ + 雪花ID
     */
    private String generateMessageId(){
        return "message_" + IdUtil.getSnowflakeNextIdStr();
    }

    @Override
    public void deleteSession(Long userId, String sessionId) {
        log.info("deleteSession: userId={}, sessionId={}", userId, sessionId);

        // 1. 校验会话
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
                        .eq(Session::getUserId, userId)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        
        //  // 2. 删除该会话的所有消息
        messageRepository.delete(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
        );
        
        // 3. 删除会话
        sessionRepository.delete(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
        );
        
    }

    @Override
    public SessionDetailResponse getSessionDetail(Long userId, String sessionId) {
        log.info("getSessionDetail: userId={}, sessionId={}", userId, sessionId);
        // 1. 校验会话
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
                        .eq(Session::getUserId, userId)
        );
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        log.info("getSessionDetail: session={}", session);
        
        // 2. 检查当前会话的所有消息
        List<Message> messages = messageRepository.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, session.getSessionId())
                        .orderByDesc(Message::getCreatedAt)
        );
        log.info("getSessionDetail: messages={}", messages);
        
        // 3. 转换为 VO
        List<SessionDetailResponse.MessageVO> messageVos = messages.stream()
                .map(msg -> SessionDetailResponse.MessageVO.builder()
                        .messageId(msg.getMessageId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .timestamp(msg.getCreatedAt())
                        .build())
                .toList();

        // 4. 构建会话详情响应
        return SessionDetailResponse.builder()
                .messages(messageVos)
                .sessionId(sessionId)
                .title(session.getTitle())
                .build();
    }

    @Override
    public List<SessionResponse> getSessionList(Long userId, Integer page, Integer size) {
        log.info("getSessionList: userId={}, page={}, size={}", userId, page, size);
        // 默认值
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        
        // 分页查询
        Page<Session> pageParam = new Page<>(page, size);
        Page<Session> pageResult = sessionRepository.selectPage(pageParam, new LambdaQueryWrapper<Session>()
                        .eq(Session::getUserId, userId)
                        .orderByDesc(Session::getCreatedAt)
        );
        
        // 转换为 SessionResponse 列表
        return pageResult.getRecords().stream()
                .map(session -> SessionResponse.builder()
                        .sessionId(session.getSessionId())
                        .title(session.getTitle())
                        .lastMessage(session.getLastMessage())
                        .messageCount(session.getMessageCount())
                        .createdAt(session.getCreatedAt())
                        .updatedAt(session.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public ChatResponse sendMessage(Long userId, SendMessageRequest request) {
        log.info("sendMessage: userId={}, request={}", userId, request);
        
        // 1.校验会话是否存在且是否属于该用户
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, request.getSessionId())
        );
        
        if (session == null || !session.getUserId().equals(userId)) {
            log.error("会话不存在或无权限访问");
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        // 限流检查
        checkRateLimit(userId);
        
        // 2. 保存用户信息
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(request.getSessionId())
                .role("user")
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.insert(message);
        
        // 如果是第一条消息，自动生成标题
        if (session.getMessageCount() == 0) {
            String title = generateTitle(request.getMessage());
            session.setTitle(title);
        }
        
        // 3. 构建多轮上下文，调用DeepSeek
        // 3.1 查询该会话最近N条消息，并按时间升序
        List<Message> historyMessages = messageRepository.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, request.getSessionId())
                        .orderByAsc(Message::getCreatedAt)
                        .last("limit " + deepSeekProperties.getMaxContextMessages())
        );
        // 3.2 转换为 DeepSeekMessage 列表
        List<DeepSeekMessage> deepSeekMessages = historyMessages.stream()
                .map(msg -> new DeepSeekMessage(
                        msg.getRole(),
                        msg.getContent()
                )).toList();

        // 3.3 调用DeepSeek获取真实回复
        String aiReply = deepSeekClient.chat(deepSeekMessages);
        
        // 4. 保存 AI 回复
        Message aiMessage = Message.builder()
                .messageId(generateMessageId())
                .sessionId(request.getSessionId())
                .role("assistant")
                .content(aiReply)
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.insert(aiMessage);
        
        // 5. 更新会话信息
        // 5.1 更新会话最后一条消息，截取前 50 个字符
        session.setLastMessage(aiReply.substring(0, Math.min(aiReply.length(), 50)) + "...");
        // 5.2 更新会话消息数，增加 2 条消息（用户消息 + AI回复）
        session.setMessageCount(session.getMessageCount() + 2);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.updateById(session);

        // 6. 返回响应
        return ChatResponse.builder()
                .messageId(aiMessage.getMessageId())
                .sessionId(request.getSessionId())
                .reply(aiReply)
                .conversationRound(session.getMessageCount() / 2)
                .timestamp(LocalDateTime.now())
                .build();
    }
    @Override
    public SessionResponse createSession(Long userId, CreateSessionRequest request) {
        log.info("createSession: userId={}, request={}", userId, request);
        // 1. 创建会话实体
        Session session = Session.builder()
                .sessionId(generateSessionId())
                .userId(userId)
                .title(request.getTitle())
                .lastMessage("")
                .messageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 2. 保存会话实体
        sessionRepository.insert(session);
        
        // 3. 返回会话响应
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .title(session.getTitle())
                .lastMessage(session.getLastMessage())
                .messageCount(session.getMessageCount())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
    
    /**
     * 使用 AI 生成会话标题
     *
     * @param title 用户输入的原始问题或内容
     * @return 生成的会话标题（不超过 10 个字）
     */
    private String generateTitle(String title){
        // 构建请求 AI 生成标题的消息
        DeepSeekMessage deepSeekMessage = new DeepSeekMessage();
        deepSeekMessage.setRole("user");
        deepSeekMessage.setContent("请根据以下问题，生成一个简短的会话标题，不超过 10 个字，只返回标题本身：" + title);
        // 调用 DeepSeek API 生成标题
        return deepSeekClient.chat(List.of(deepSeekMessage));
    }
    
    private void checkRateLimit(Long userId) {
        String key = "chat:rate:" + userId;

        Long count = redisTemplate.opsForValue().increment(key);
        
        // 第一次：设置60秒过期
        if (count != null && count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        
        if (count != null && count >= deepSeekProperties.getMaxMessagesPerMinute()) {
            log.error("用户 {} 创建会话频率过高", userId);
            throw new BusinessException(ResultCode.CHAT_RATE_LIMIT_EXCEEDED);
        }
        
    }
}
