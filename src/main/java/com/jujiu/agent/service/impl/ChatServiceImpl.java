package com.jujiu.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.common.constant.BusinessConstants;
import com.jujiu.agent.common.constant.RedisKeys;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.DeepSeekProperties;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.deepseek.DeepSeekRequest;
import com.jujiu.agent.model.dto.deepseek.DeepSeekResponse;
import com.jujiu.agent.model.dto.deepseek.ToolCallDTO;
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
import com.jujiu.agent.service.FunctionCallingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
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
    
    @Autowired
    private FunctionCallingService functionCallingService;

    @Autowired
    private ExecutorService chatExecutor;
    
    @Autowired
    private ObjectMapper objectMapper;
    
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
        log.info("[CHAT][DELETE_SESSION] 开始删除会话 - userId={}, sessionId={}", userId, sessionId);

        // 1. 校验会话
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
                        .eq(Session::getUserId, userId)
        );
        if (session == null) {
            log.warn("[CHAT][DELETE_SESSION] 会话不存在或无权限 - userId={}, sessionId={}", userId, sessionId);
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
        
        log.info("[CHAT][DELETE_SESSION_SUCCESS] 会话删除成功 - sessionId={}", sessionId);
    }

    @Override
    public SessionDetailResponse getSessionDetail(Long userId, String sessionId) {
        log.info("[CHAT][GET_SESSION_DETAIL] 请求获取会话详情 - userId={}, sessionId={}", userId, sessionId);
        
        // 1. 校验会话
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
                        .eq(Session::getUserId, userId)
        );
        if (session == null) {
            log.warn("[CHAT][GET_SESSION_DETAIL] 会话不存在或无权限 - userId={}, sessionId={}", userId, sessionId);
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        log.debug("[CHAT][GET_SESSION_DETAIL] 会话基本信息 - session={}", session);
        
        // 2. 检查当前会话的所有消息
        List<Message> messages = messageRepository.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, session.getSessionId())
                        .orderByDesc(Message::getCreatedAt)
        );
        log.debug("[CHAT][GET_SESSION_DETAIL] 查询到 {} 条消息", messages.size());
        
        // 3. 转换为 VO
        List<SessionDetailResponse.MessageVO> messageVos = messages.stream()
                .map(msg -> SessionDetailResponse.MessageVO.builder()
                        .messageId(msg.getMessageId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .timestamp(msg.getCreatedAt())
                        .toolCalls(msg.getToolCalls())
                        .build())
                .toList();

        // 4. 构建会话详情响应
        log.info("[CHAT][GET_SESSION_DETAIL_SUCCESS] 获取会话详情成功 - sessionId={}, title={}, messageCount={}", 
                sessionId, session.getTitle(), messageVos.size());
        return SessionDetailResponse.builder()
                .messages(messageVos)
                .sessionId(sessionId)
                .title(session.getTitle())
                .build();
    }

    @Override
    public List<SessionResponse> getSessionList(Long userId, Integer page, Integer size) {
        log.info("[CHAT][GET_SESSION_LIST] 请求获取会话列表 - userId={}, page={}, size={}", userId, page, size);
        
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
        
        log.info("[CHAT][GET_SESSION_LIST_SUCCESS] 获取会话列表成功 - userId={}, totalRecords={}", 
                userId, pageResult.getTotal());
        
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
    @Transactional
    public ChatResponse sendMessage(Long userId, SendMessageRequest request) {
        log.info("[CHAT][SEND_MESSAGE] 收到发送消息请求 - userId={}, sessionId={}, messageLength={}", 
                userId, request.getSessionId(), request.getMessage().length());
        
        // 1.校验会话是否存在且是否属于该用户
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, request.getSessionId())
        );
        
        if (session == null || !session.getUserId().equals(userId)) {
            log.error("[CHAT][SEND_MESSAGE] 会话不存在或无权限访问 - userId={}, sessionId={}", 
                    userId, request.getSessionId());
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        // 限流检查
        checkRateLimit(userId);
        
        // 2. 保存用户消息
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(request.getSessionId())
                .role(BusinessConstants.ROLE_USER)
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.insert(message);
        
        log.info("[CHAT][MESSAGE_SAVED] 用户消息保存成功 - messageId={}, sessionId={}", 
                message.getMessageId(), request.getSessionId());
        
        // 如果是第一条消息，自动生成标题
        if (session.getMessageCount() == 0) {
            String title = generateTitle(request.getMessage());
            session.setTitle(title);
            log.info("[CHAT][AUTO_TITLE] 为会话自动生成标题 - sessionId={}, title={}", 
                    request.getSessionId(), title);
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
        List<DeepSeekMessage> deepSeekMessages = new ArrayList<>(historyMessages.stream()
                .map(msg -> {
                    DeepSeekMessage deepSeekMessage = new DeepSeekMessage();
                    String roleStr = msg.getRole();
                    DeepSeekMessage.MessageRole role = DeepSeekMessage.MessageRole.fromValue(roleStr);                    deepSeekMessage.setRole(role);
                    deepSeekMessage.setContent(msg.getContent());
                    return deepSeekMessage;
                }).toList());
        
        // 3.3 在列表最前面插入 system 消息
        DeepSeekMessage systemMessage = new DeepSeekMessage();
        systemMessage.setRole(DeepSeekMessage.MessageRole.SYSTEM);
        systemMessage.setContent(deepSeekProperties.getSystemPrompt());
        deepSeekMessages.add(0, systemMessage);
        
        // 3.4 调用 DeepSeek可执行工具的接口获取回复
        log.info("[CHAT][DEEPSEEK_CALL] 开始调用 DeepSeek API - sessionId={}, contextSize={}", 
                request.getSessionId(), deepSeekMessages.size());
//        DeepSeekResult result = deepSeekClient.chat(deepSeekMessages);
        DeepSeekResult result = functionCallingService.chatWithTools(deepSeekMessages);
        String aiReply = result.getReply();
        log.info("[CHAT][DEEPSEEK_RESPONSE] DeepSeek 返回成功 - sessionId={}, totalTokens={}, promptTokens={}, completionTokens={}", 
                request.getSessionId(), result.getTotalTokens(), result.getPromptTokens(), result.getCompletionTokens());

        // 回填用户消息的 token
        message.setTokens(result.getPromptTokens());
        messageRepository.updateById(message);
        
        log.debug("[CHAT][TOKEN_UPDATE] 用户消息 Token 更新成功 - messageId={}, promptTokens={}", 
                message.getMessageId(), result.getPromptTokens());
        
        // 4. 保存 AI 回复
        Message aiMessage = Message.builder()
                .messageId(generateMessageId())
                .sessionId(request.getSessionId())
                .role(BusinessConstants.ROLE_ASSISTANT)
                .content(aiReply)
                .createdAt(LocalDateTime.now())
                .tokens(result.getCompletionTokens())
                .build();
        messageRepository.insert(aiMessage);
        
        log.info("[CHAT][AI_MESSAGE_SAVED] AI 消息保存成功 - messageId={}, sessionId={}, tokens={}", 
                aiMessage.getMessageId(), request.getSessionId(), result.getCompletionTokens());
        
        // 5. 更新会话信息
        // 5.1 更新会话最后一条消息，截取前 50 个字符
        session.setLastMessage(aiReply.substring(0, Math.min(aiReply.length(), BusinessConstants.MAX_LAST_MESSAGE_PREVIEW)) + "...");
        // 5.2 更新会话消息数，增加 2 条消息（用户消息 + AI 回复）
        session.setMessageCount(session.getMessageCount() + 2);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.updateById(session);
                
        log.info("[CHAT][SESSION_UPDATED] 会话信息更新成功 - sessionId={}, totalRounds={}, lastMessageLength={}", 
                request.getSessionId(), session.getMessageCount() / 2, session.getLastMessage().length());

        log.info("[CHAT][SEND_MESSAGE_COMPLETE] 发送消息流程完成 - sessionId={}, messageId={}, conversationRound={}", 
                request.getSessionId(), aiMessage.getMessageId(), session.getMessageCount() / 2);

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
        log.info("[CHAT][CREATE_SESSION] 收到创建会话请求 - userId={}, title={}", userId, request.getTitle());
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
        
        log.info("[CHAT][SESSION_CREATED] 会话保存成功 - sessionId={}, userId={}, title={}", 
                session.getSessionId(), userId, session.getTitle());
        
        log.info("[CHAT][CREATE_SESSION_COMPLETE] 创建会话流程完成 - sessionId={}, userId={}", 
                session.getSessionId(), userId);
        
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
        deepSeekMessage.setRole(DeepSeekMessage.MessageRole.USER);
        deepSeekMessage.setContent("请根据以下问题，生成一个简短的会话标题，不超过 10 个字，只返回标题本身：" + title);
        // 调用 DeepSeek API 生成标题
        DeepSeekResult result = deepSeekClient.chat(List.of(deepSeekMessage));
        return result.getReply();
    }

    private void checkRateLimit(Long userId) {
        String key = RedisKeys.getChatRateKey(userId);

        Long count = redisTemplate.opsForValue().increment(key);
        
        int maxPerMinute = deepSeekProperties.getMaxMessagesPerMinute();
        
        // 第一次：设置时间窗口过期
        if (count != null && count == 1) {
            redisTemplate.expire(key, maxPerMinute, TimeUnit.MILLISECONDS);
        }

        if (count != null && count > maxPerMinute) {
            log.error("用户 {} 发送消息频率过高", userId);
            throw new BusinessException(ResultCode.CHAT_RATE_LIMIT_EXCEEDED);
        }

    }

    /**
     * 发送消息（流式响应）
     * 处理用户的聊天请求，通过 SSE 方式实时返回 AI 的流式响应内容。
     * 包括会话验证、限流检查、消息保存、上下文构建、调用 DeepSeek 流式接口等功能。
     *
     * @param userId 用户 ID，用于标识当前登录用户
     * @param request 发送消息的请求对象，包含会话 ID 和消息内容
     * @return SseEmitter SSE 发射器，用于向客户端推送流式响应数据
     */
    @Override
    public SseEmitter sendMessageStream(Long userId, SendMessageRequest request) {
        log.info("[CHAT][SEND_MESSAGE_STREAM] 收到消息流请求 - userId={}, sessionId={}", userId, request.getSessionId());
        
        // 1. 创建 SseEmitter，设置 3 分钟超时时间
        SseEmitter emitter = new SseEmitter(BusinessConstants.SSE_TIMEOUT);
        
        // 2. 校验会话是否存在且属于当前用户
        Session session = sessionRepository.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, request.getSessionId())
        );
        if (session == null || !session.getUserId().equals(userId)) {
            log.error("[CHAT][SEND_MESSAGE_STREAM] 会话不存在 - sessionId={}", request.getSessionId());
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        
        // 3. 执行限流检查，防止 API 调用频率过高
        checkRateLimit(userId);
        
        // 4. 保存用户发送的消息到数据库
        Message message = Message.builder()
                .messageId(generateMessageId())
                .sessionId(request.getSessionId())
                .role(BusinessConstants.ROLE_USER)
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepository.insert(message);
        
        log.info("[CHAT][SEND_MESSAGE_STREAM] 用户消息保存成功 - messageId={}, sessionId={}",
                message.getMessageId(), request.getSessionId());
        
        // 5. 如果是会话的第一条消息，自动生成会话标题
        if (session.getMessageCount() == 0) {
            session.setTitle(generateTitle(request.getMessage()));
            
            // 5.1 更新会话标题
            sessionRepository.updateById(session);
            
            log.info("[CHAT][SEND_MESSAGE_STREAM] 会话第一次消息，自动生成标题 - sessionId={}, title={}",
                    request.getSessionId(), session.getTitle());
        }
        
        // 6. 查询历史消息，构建多轮对话上下文
        // 先查询所有消息按时间正序
        List<Message> allMessages = messageRepository.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, request.getSessionId())
                        .orderByAsc(Message::getCreatedAt)
        );

        // 如果消息数超过限制，只取最新的 N 条
        List<Message> historyMessages;
        int maxMessages = deepSeekProperties.getMaxContextMessages();
        if (allMessages.size() > maxMessages) {
            historyMessages = allMessages.subList(allMessages.size() - maxMessages, allMessages.size());
        } else {
            historyMessages = allMessages;
        }
        
        // 7. 将历史消息转换为 DeepSeek API 所需的消息格式
        List<DeepSeekMessage> deepSeekMessages = new ArrayList<>(historyMessages.stream()
                .map(msg -> {
                    DeepSeekMessage deepSeekMessage = new DeepSeekMessage();
                    deepSeekMessage.setRole(DeepSeekMessage.MessageRole.fromValue(msg.getRole()));
                    String content = msg.getContent();

                    // tool 消息的 content 不能为 null 或空字符串，必须有内容
                    if ("tool".equals(msg.getRole())) {
                        deepSeekMessage.setContent(content != null && !content.isEmpty() ? content : "工具执行结果为空");
                    } else {
                        // assistant/user 消息：空字符串转为 null
                        deepSeekMessage.setContent((content == null || content.isEmpty()) ? null : content);
                    }

                    // 恢复tool_calls
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        try {
                            List<ToolCallDTO> toolCalls = objectMapper.readValue(
                                    msg.getToolCalls(),
                                    new TypeReference<List<ToolCallDTO>>() {}
                            );
                            deepSeekMessage.setToolCalls(toolCalls);
                        } catch (Exception e) {
                            log.warn("[CHAT] 解析历史消息tool_calls失败 - messageId={}", msg.getMessageId(), e);
                        }
                    }
                    // 恢复tool_call_id
                    if (msg.getToolCallId() != null && !msg.getToolCallId().isEmpty()) {
                        deepSeekMessage.setToolCallId(msg.getToolCallId());
                    }
                    return deepSeekMessage;
                }).toList());
        
        // 在消息列表开头添加系统提示词，设定 AI 角色和行为准则
        DeepSeekMessage systemMessage = new DeepSeekMessage();
        systemMessage.setRole(DeepSeekMessage.MessageRole.SYSTEM);
        systemMessage.setContent(deepSeekProperties.getSystemPrompt());
        deepSeekMessages.add(0, systemMessage);

        // 提交到线程池
        chatExecutor.submit(() -> {
            try {
                FunctionCallingService.StreamingChatResult result = functionCallingService.streamChatWithTools(
                        deepSeekMessages,
                        event -> {
                            // 转发事件给前端
                            try {
                                emitter.send(SseEmitter.event()
                                        .name(event.getType())
                                        .data(event));
                            } catch (IOException e) {
                                log.error("[CHAT][SEND_MESSAGE_STREAM] 推送内容给客户端出错 - sessionId={}", request.getSessionId(), e);
                                throw new RuntimeException(e);
                            }
                        }
                );

                // 8. 保存所有中间消息（包括带tool_calls的assistant消息和tool消息）
                for (DeepSeekMessage msg : result.getMessagesToSave()) {
                    // 判断是assistant消息（有tool_calls）还是tool消息（有toolCallId）
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        // 这是带 tool_calls 的 assistant 消息，需要保存
                        Message toolCallMessage = Message.builder()
                                .messageId(generateMessageId())
                                .sessionId(request.getSessionId())
                                .role(msg.getRole().getValue())
                                .content(msg.getContent())   // ← 直接传，null就是null，DeepSeek允许tool_calls消息content为null
                                .toolCalls(objectMapper.writeValueAsString(msg.getToolCalls()))
                                .createdAt(LocalDateTime.now())
                                .tokens(0)  // 中间消息不统计token
                                .build();
                        messageRepository.insert(toolCallMessage);
                    } else if (msg.getToolCallId() != null) {
                        // 保存tool消息（工具返回结果）
                        Message toolMessage = Message.builder()
                                .messageId(generateMessageId())
                                .sessionId(request.getSessionId())
                                .role(msg.getRole().getValue())
                                .content(msg.getContent())
                                .toolCallId(msg.getToolCallId())  // 保存tool_call_id
                                .createdAt(LocalDateTime.now())
                                .tokens(0)
                                .build();
                        messageRepository.insert(toolMessage);
                    }
                }
                
                // 8. 流结束，保存AI信息
                log.info("[CHAT][SEND_MESSAGE_STREAM] 流结束，保存ai信息 - sessionId={}", request.getSessionId());

                // 8.1 构建 AI 消息
                Message aiMessage = Message.builder()
                        .messageId(generateMessageId())
                        .sessionId(request.getSessionId())
                        .role(BusinessConstants.ROLE_ASSISTANT)
                        .content(result.getFinalReply())
                        .createdAt(LocalDateTime.now())
                        .tokens(result.getCompletionTokens())
                        .build();

                messageRepository.insert(aiMessage);
                log.info("[CHAT][SEND_MESSAGE_STREAM] AI消息保存成功 - messageId={}, sessionId={}, role={}, content={}, createdAt={}, tokens={}",
                        aiMessage.getMessageId(), request.getSessionId(), aiMessage.getRole(), aiMessage.getContent(), aiMessage.getCreatedAt(), aiMessage.getTokens());
                
                // 9. 回填用户消息的promptTokens
                message.setTokens(result.getPromptTokens());
                messageRepository.updateById(message);

                // 10. 更新会话
                session.setLastMessage(result.getFinalReply().substring(0, Math.min(result.getFinalReply().length(), BusinessConstants.MAX_LAST_MESSAGE_PREVIEW)) + "...");
                session.setMessageCount(session.getMessageCount() + 2);
                session.setUpdatedAt(LocalDateTime.now());
                sessionRepository.updateById(session);

                // 11. 发送done事件
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(result));

                // 12. 完成
                emitter.complete();
                log.info("[CHAT][SEND_MESSAGE_STREAM] 消息流处理完成 - sessionId={}", request.getSessionId());
            } catch (Exception e) {
                log.error("[CHAT][SEND_MESSAGE_STREAM] 处理消息流时出错 - sessionId={}", request.getSessionId(), e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
