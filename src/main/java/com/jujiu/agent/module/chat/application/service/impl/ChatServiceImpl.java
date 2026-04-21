package com.jujiu.agent.module.chat.application.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekClient;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekResult;
import com.jujiu.agent.module.chat.infrastructure.mapper.MessageMapper;
import com.jujiu.agent.module.chat.infrastructure.mapper.SessionMapper;
import com.jujiu.agent.shared.constant.BusinessConstants;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.chat.infrastructure.config.DeepSeekProperties;
import com.jujiu.agent.module.chat.infrastructure.deepseek.DeepSeekMessage;
import com.jujiu.agent.module.chat.infrastructure.deepseek.ToolCallDTO;
import com.jujiu.agent.module.chat.api.request.CreateSessionRequest;
import com.jujiu.agent.module.chat.api.request.SendMessageRequest;
import com.jujiu.agent.module.chat.api.response.ChatResponse;
import com.jujiu.agent.module.chat.api.response.SessionDetailResponse;
import com.jujiu.agent.module.chat.api.response.SessionResponse;
import com.jujiu.agent.module.chat.domain.entity.Message;
import com.jujiu.agent.module.chat.domain.entity.Session;
import com.jujiu.agent.module.chat.application.service.ChatPersistenceService;
import com.jujiu.agent.module.chat.application.service.ChatRateLimitService;
import com.jujiu.agent.module.chat.application.service.ChatService;
import com.jujiu.agent.module.chat.application.service.FunctionCallingService;
import com.jujiu.agent.module.kb.application.service.RagService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 聊天服务实现类。负责会话管理、消息发送、流式响应、知识库增强等核心聊天功能。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 14:35
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {
    /** 消息仓储。 */
    private final MessageMapper messageMapper;
    /** 会话仓储。 */
    private final SessionMapper sessionMapper;
    /** DeepSeek 客户端。 */
    private final DeepSeekClient deepSeekClient;
    /** DeepSeek 配置属性。 */
    private final DeepSeekProperties deepSeekProperties;
    /** 函数调用服务。 */
    private final FunctionCallingService functionCallingService;
    /** 聊天任务线程池。 */
    private final ExecutorService chatExecutor;
    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper;
    /** 聊天限流服务。 */
    private final ChatRateLimitService chatRateLimitService;
    /** 聊天持久化服务。 */
    private final ChatPersistenceService chatPersistenceService;
    /** RAG 知识库服务。 */
    private final RagService ragService;
    
    /**
     * 构造方法。
     *
     * @param messageMapper       消息仓储
     * @param sessionMapper       会话仓储
     * @param deepSeekClient          DeepSeek 客户端
     * @param deepSeekProperties      DeepSeek 配置属性
     * @param functionCallingService  函数调用服务
     * @param chatExecutor            聊天任务线程池
     * @param objectMapper            JSON 序列化器
     * @param chatRateLimitService    聊天限流服务
     * @param chatPersistenceService  聊天持久化服务
     * @param ragService              RAG 知识库服务
     */
    public ChatServiceImpl(MessageMapper messageMapper,
                           SessionMapper sessionMapper,
                           DeepSeekClient deepSeekClient,
                           DeepSeekProperties deepSeekProperties,
                           FunctionCallingService functionCallingService,
                           ExecutorService chatExecutor,
                           ObjectMapper objectMapper,
                           ChatRateLimitService chatRateLimitService,
                           ChatPersistenceService chatPersistenceService,
                           RagService ragService) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.deepSeekClient = deepSeekClient;
        this.deepSeekProperties = deepSeekProperties;
        this.functionCallingService = functionCallingService;
        this.chatExecutor = chatExecutor;
        this.objectMapper = objectMapper;
        this.chatRateLimitService = chatRateLimitService;
        this.chatPersistenceService = chatPersistenceService;
        this.ragService = ragService;
    }

    /**
     * 生成会话 ID。
     * 格式为 session_ 拼接雪花算法生成的唯一字符串。
     *
     * @return 会话 ID
     */
    private String generateSessionId(){
        return "session_" + IdUtil.getSnowflakeNextIdStr();
    }
    

    @Override
    @Transactional
    public void deleteSession(Long userId, String sessionId) {
        log.info("[CHAT][DELETE_SESSION] 开始删除会话 - userId={}, sessionId={}", userId, sessionId);

        // 1. 校验会话
        Session session = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
                        .eq(Session::getUserId, userId)
        );
        if (session == null) {
            log.warn("[CHAT][DELETE_SESSION] 会话不存在或无权限 - userId={}, sessionId={}", userId, sessionId);
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        
        //  // 2. 删除该会话的所有消息
        chatPersistenceService.deleteSessionMessages(sessionId);
        
        // 3. 删除会话
        sessionMapper.delete(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, sessionId)
        );
        
        log.info("[CHAT][DELETE_SESSION_SUCCESS] 会话删除成功 - sessionId={}", sessionId);
    }

    @Override
    public SessionDetailResponse getSessionDetail(Long userId, String sessionId) {
        log.info("[CHAT][GET_SESSION_DETAIL] 请求获取会话详情 - userId={}, sessionId={}", userId, sessionId);
        
        // 1. 校验会话
        Session session = sessionMapper.selectOne(
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
        List<Message> messages = messageMapper.selectList(
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
        
        // 1. 参数处理：page 和 size 默认值及边界校验
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 10 : size;
        
        if (pageNumber < 0) {
            throw new BusinessException(ResultCode.INVALID_PAGE_NUMBER);
        }
        if (pageSize <= 0) {
            throw new BusinessException(ResultCode.INVALID_PAGE_SIZE);
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        
        // 2. MyBatis-Plus 分页查询当前用户的会话列表
        long dbPageNumber = (long) pageNumber + 1;
        Page<Session> pageParam = new Page<>(dbPageNumber, pageSize);
        Page<Session> pageResult = sessionMapper.selectPage(pageParam, new LambdaQueryWrapper<Session>()
                        .eq(Session::getUserId, userId)
                        .orderByDesc(Session::getCreatedAt)
        );
        
        log.info("[CHAT][GET_SESSION_LIST_SUCCESS] 获取会话列表成功 - userId={}, totalRecords={}", 
                userId, pageResult.getTotal());
        
        // 3. 将会话实体列表转换为响应对象列表
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

        // 1. 准备会话和消息：校验会话、限流、保存用户消息、构建上下文
        ChatPrepareResult prepareResult = prepareChat(userId, request);
        Session session = prepareResult.getSession();
        Message message = prepareResult.getUserMessage();
        List<DeepSeekMessage> deepSeekMessages = prepareResult.getDeepSeekMessages();
        
        // 2. 调用 DeepSeek 函数调用接口获取 AI 回复
        log.info("[CHAT][DEEPSEEK_CALL] 开始调用 DeepSeek API - sessionId={}, contextSize={}", 
                request.getSessionId(), deepSeekMessages.size());
        
        DeepSeekResult result = functionCallingService.chatWithTools(userId, deepSeekMessages);
        String aiReply = result.getReply();
        
        log.info("[CHAT][DEEPSEEK_RESPONSE] DeepSeek 返回成功 - sessionId={}, totalTokens={}, promptTokens={}, completionTokens={}", 
                request.getSessionId(), result.getTotalTokens(), result.getPromptTokens(), result.getCompletionTokens());

        // 3. 回填用户消息的 promptTokens
        updateUserMessageTokens(message, result.getPromptTokens());
        
        log.debug("[CHAT][TOKEN_UPDATE] 用户消息 Token 更新成功 - messageId={}, promptTokens={}", 
                message.getMessageId(), result.getPromptTokens());
        
        // 4. 保存 AI 回复消息
        Message aiMessage = chatPersistenceService.saveAssistantMessage(
                request.getSessionId(),
                aiReply,
                result.getCompletionTokens());
        log.info("[CHAT][AI_MESSAGE_SAVED] AI 消息保存成功 - messageId={}, sessionId={}, tokens={}", 
                aiMessage.getMessageId(), request.getSessionId(), result.getCompletionTokens());
        
        // 5. 更新会话信息（最后消息、消息数、时间戳）
        chatPersistenceService.updateSessionAfterReply(session, aiReply, 2);
        
        log.info("[CHAT][SESSION_UPDATED] 会话信息更新成功 - sessionId={}, totalRounds={}, lastMessageLength={}", 
                request.getSessionId(), session.getMessageCount() / 2, session.getLastMessage().length());

        log.info("[CHAT][SEND_MESSAGE_COMPLETE] 发送消息流程完成 - sessionId={}, messageId={}, conversationRound={}", 
                request.getSessionId(), aiMessage.getMessageId(), session.getMessageCount() / 2);

        // 6. 构建并返回响应对象
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
        
        // 1. 构建新的会话实体
        Session session = Session.builder()
                .sessionId(generateSessionId())
                .userId(userId)
                .title(request.getTitle())
                .lastMessage("")
                .messageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 2. 持久化会话实体到数据库
        sessionMapper.insert(session);
        
        log.info("[CHAT][SESSION_CREATED] 会话保存成功 - sessionId={}, userId={}, title={}", 
                session.getSessionId(), userId, session.getTitle());
        
        log.info("[CHAT][CREATE_SESSION_COMPLETE] 创建会话流程完成 - sessionId={}, userId={}", 
                session.getSessionId(), userId);
        
        // 3. 构建并返回会话响应对象
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
        // 1. 构建请求 AI 生成标题的消息
        DeepSeekMessage deepSeekMessage = new DeepSeekMessage();
        deepSeekMessage.setRole(DeepSeekMessage.MessageRole.USER);
        deepSeekMessage.setContent("请根据以下问题，生成一个简短的会话标题，不超过 10 个字，只返回标题本身：" + title);
        // 2. 调用 DeepSeek API 生成标题并返回
        DeepSeekResult result = deepSeekClient.chat(List.of(deepSeekMessage));
        return result.getReply();
    }

    /**
     * 加载指定会话的历史消息列表。
     *
     * @param sessionId 会话 ID
     * @return 按创建时间升序排列的历史消息列表，受最大上下文条数限制
     */
    private List<Message> loadHistoryMessages(String sessionId) {
        // 查询会话历史消息，按时间升序排列并限制最大条数
        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByAsc(Message::getCreatedAt)
                        .last("limit " + deepSeekProperties.getMaxContextMessages())
        );
    }

    /**
     * 构建多轮对话上下文
     * 将历史消息转换为 DeepSeek API 所需的格式，并在开头添加系统提示词。
     * 支持普通消息、工具调用请求和工具执行结果的转换。
     *
     * @param historyMessages 历史消息列表，包含当前会话的所有历史对话记录
     * @return List<DeepSeekMessage> 构建完成的对话上下文，包含系统消息和历史对话
     */
    private List<DeepSeekMessage> buildChatContext(List<Message> historyMessages) {
        // 1. 将历史消息逐条转换为 DeepSeekMessage 格式
        List<DeepSeekMessage> deepSeekMessages = new ArrayList<>(historyMessages.stream()
                .map(msg -> {
                    DeepSeekMessage deepSeekMessage = new DeepSeekMessage();
                    DeepSeekMessage.MessageRole role =
                            DeepSeekMessage.MessageRole.fromValue(msg.getRole());
                    deepSeekMessage.setRole(role);

                    // 根据消息角色设置内容（TOOL 类型需要特殊处理空内容）
                    String content = msg.getContent();
                    if (role == DeepSeekMessage.MessageRole.TOOL) {
                        deepSeekMessage.setContent(content != null && !content.isEmpty() ?
                                content : "工具执行结果为空");
                    } else {
                        deepSeekMessage.setContent((content == null || content.isEmpty()) ?
                                null : content);
                    }

                    // 解析并设置工具调用信息
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        try {
                            List<ToolCallDTO> toolCalls = objectMapper.readValue(
                                    msg.getToolCalls(),
                                    new TypeReference<List<ToolCallDTO>>() {}
                            );
                            deepSeekMessage.setToolCalls(toolCalls);
                        } catch (Exception e) {
                            log.warn("[CHAT] 解析历史消息 tool_calls 失败 - messageId={}",
                                    msg.getMessageId(), e);
                        }
                    }

                    // 设置工具调用 ID
                    if (msg.getToolCallId() != null && !msg.getToolCallId().isEmpty()) {
                        deepSeekMessage.setToolCallId(msg.getToolCallId());
                    }

                    return deepSeekMessage;
                }).toList());
        
        // 2. 在消息列表开头插入系统提示词
        DeepSeekMessage systemMessage = new DeepSeekMessage();
        systemMessage.setRole(DeepSeekMessage.MessageRole.SYSTEM);
        systemMessage.setContent(deepSeekProperties.getSystemPrompt());
        deepSeekMessages.add(0, systemMessage);

        return deepSeekMessages;
    }
    
    /**
     * 按需追加知识库增强上下文。
     *
     * <p>当请求显式开启知识库增强时，先从知识库检索与当前问题相关的片段，
     * 再以系统消息形式插入到对话上下文中，供后续模型生成与工具调用复用。
     *
     * @param userId 当前用户 ID
     * @param request 聊天请求
     * @param deepSeekMessages 当前对话上下文
     */
    private void appendKnowledgeContextIfNeeded(Long userId,
                                                SendMessageRequest request,
                                                List<DeepSeekMessage> deepSeekMessages) {
        // 1. 若未开启知识库增强，直接返回
        if (request.getEnableKnowledgeBase() == null || !request.getEnableKnowledgeBase()) {
            return;
        }

        log.info("[CHAT][KB_ENHANCE] 检测到知识库增强已开启 - userId={}, sessionId={}, kbId={}, topK={}",
                userId,
                request.getSessionId(),
                request.getKnowledgeBaseId(),
                request.getRetrievalTopK()
        );
        
        // 2. 调用 RAG 服务构建与当前问题相关的知识上下文
        String knowledgeContext = ragService.buildKnowledgeContext(userId,
                request.getKnowledgeBaseId(),
                request.getMessage(),
                request.getRetrievalTopK()
        );

        // 3. 若未检索到可用上下文，记录日志后返回
        if (knowledgeContext == null || knowledgeContext.isBlank()) {
            log.info("[CHAT][KB_ENHANCE][ACL] 未检索到当前用户可注入知识上下文 - userId={}, sessionId={}, kbId={}",
                    userId, request.getSessionId(), request.getKnowledgeBaseId());
            return;
        }

        // 4. 构建知识库增强系统消息并插入到对话上下文中的合适位置
        DeepSeekMessage knowledgeMessage = new DeepSeekMessage();
        knowledgeMessage.setRole(DeepSeekMessage.MessageRole.SYSTEM);
        knowledgeMessage.setContent(buildKnowledgeEnhancementPrompt(knowledgeContext));
        
        int insertIndex = deepSeekMessages.isEmpty() ? 0 : 1;
        deepSeekMessages.add(insertIndex, knowledgeMessage);

        log.info("[CHAT][KB_ENHANCE] 知识上下文注入完成 - userId={}, sessionId={}, insertIndex={}, contextLength={}",
                userId, request.getSessionId(), insertIndex, knowledgeContext.length());
        
        log.info("[CHAT][KB_ENHANCE] 当前对话已启用显式知识增强，建议模型优先基于已注入资料回答 - userId={}, sessionId={}",
                userId, request.getSessionId());
    }

    /**
     * 构造知识库增强提示词。
     *
     * @param knowledgeContext 知识库上下文
     * @return 系统提示文本
     */
    private String buildKnowledgeEnhancementPrompt(String knowledgeContext) {
        return """
            以下是与当前问题相关的知识库参考资料，已经提前完成检索，请优先使用这些资料回答当前问题。

            回答要求：
            1. 如果以下资料已经足够回答当前问题，请直接基于资料作答
            2. 不要重复调用 knowledge_base 工具去查询相同内容
            3. 只有当以下资料明显不足以回答问题时，才考虑调用其他必要工具
            4. 不要编造资料中不存在的事实

            【知识库参考资料】
            %s
            """.formatted(knowledgeContext);
    }

    /**
     * 准备聊天对话
     * 执行聊天前的所有准备工作，包括会话验证、限流检查、消息保存和上下文构建。
     *
     * @param userId 用户 ID，用于标识当前登录用户
     * @param request 发送消息的请求对象，包含会话 ID 和消息内容
     * @return ChatPrepareResult 聊天准备结果，包含会话信息、用户消息和对话上下文
     */
    private ChatPrepareResult prepareChat(Long userId, SendMessageRequest request) {
        // 1. 校验会话是否存在且属于当前用户
        Session session = sessionMapper.selectOne(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getSessionId, request.getSessionId())
        );

        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND);
        }
        
        // 2. 执行限流检查，防止 API 调用频率过高
        chatRateLimitService.checkChatRateLimit(userId);

        // 3. 保存用户消息到数据库
        Message userMessage = chatPersistenceService.saveUserMessage(request.getSessionId(),
                request.getMessage());

        // 4. 如果是会话的第一条消息，自动生成会话标题
        if (session.getMessageCount() == 0) {
            String title = generateTitle(request.getMessage());
            chatPersistenceService.updateSessionTitle(session, title);
            log.info("[CHAT][AUTO_TITLE] 为会话自动生成标题 - sessionId={}",
                    request.getSessionId());
        }

        // 5. 查询历史消息并构建多轮对话上下文
        List<Message> historyMessages = loadHistoryMessages(request.getSessionId());
        List<DeepSeekMessage> deepSeekMessages = buildChatContext(historyMessages);
        
        // 6. 如显式启用知识库增强，则插入知识上下文
        appendKnowledgeContextIfNeeded(userId, request, deepSeekMessages);
        
        return new ChatPrepareResult(session, userMessage, deepSeekMessages);
    }
    

    /**
     * 聊天准备结果
     * 封装 prepareChat 方法返回的结果数据
     */
    @Getter
    private static class ChatPrepareResult {
        private final Session session;
        private final Message userMessage;
        private final List<DeepSeekMessage> deepSeekMessages;

        private ChatPrepareResult(Session session, Message userMessage, List<DeepSeekMessage>
                deepSeekMessages) {
            this.session = session;
            this.userMessage = userMessage;
            this.deepSeekMessages = deepSeekMessages;
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
        
        // 2. 准备聊天对话：校验会话、限流、保存用户消息、构建上下文
        ChatPrepareResult prepareResult = prepareChat(userId, request);
        
        // 3. 获取会话信息、用户消息和对话上下文
        Session session = prepareResult.getSession();
        Message message = prepareResult.getUserMessage();
        List<DeepSeekMessage> deepSeekMessages = prepareResult.getDeepSeekMessages();

        // 4. 将流式处理任务提交到线程池，避免阻塞主线程
        chatExecutor.submit(() -> {
            try {
                // 5. 调用 DeepSeek 流式接口，实时转发事件给前端
                FunctionCallingService.StreamingChatResult result = functionCallingService.streamChatWithTools(
                        userId,
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

                // 6. 保存所有中间消息（包括带 tool_calls 的 assistant 消息和 tool 消息）
                chatPersistenceService.saveIntermediateMessages(request.getSessionId(), result.getMessagesToSave());
                
                // 7. 流结束，保存 AI 最终回复消息
                log.info("[CHAT][SEND_MESSAGE_STREAM] 流结束，保存ai信息 - sessionId={}", request.getSessionId());

                Message aiMessage = chatPersistenceService.saveAssistantMessage(
                        request.getSessionId(),
                        result.getFinalReply(),
                        result.getCompletionTokens());
                log.info("[CHAT][SEND_MESSAGE_STREAM] AI消息保存成功 - messageId={}, sessionId={}, role={}, createdAt={}, tokens={}, contentLength={}",
                        aiMessage.getMessageId(),
                        request.getSessionId(),
                        aiMessage.getRole(),
                        aiMessage.getCreatedAt(),
                        aiMessage.getTokens(),
                        aiMessage.getContent() == null ? 0 : aiMessage.getContent().length());
                
                // 8. 回填用户消息的 promptTokens
                updateUserMessageTokens(message, result.getPromptTokens());
                
                // 9. 更新会话信息
                chatPersistenceService.updateSessionAfterReply(session, result.getFinalReply(), 2);
                
                // 10. 发送 done 事件，告知前端流已结束
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(result));

                // 11. 正常完成 SSE 连接
                emitter.complete();
                log.info("[CHAT][SEND_MESSAGE_STREAM] 消息流处理完成 - sessionId={}", request.getSessionId());
            } catch (Exception e) {
                log.error("[CHAT][SEND_MESSAGE_STREAM] 处理消息流时出错 - sessionId={}", request.getSessionId(), e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 更新用户消息的 Token 数量。
     *
     * @param message      用户消息实体
     * @param promptTokens prompt Tokens 数量
     */
    private void updateUserMessageTokens(Message message, Integer promptTokens) {
        // 设置用户消息的 Tokens 并更新到数据库
        message.setTokens(promptTokens);
        messageMapper.updateById(message);
    }
}
