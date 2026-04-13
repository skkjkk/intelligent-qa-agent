package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.config.DeepSeekProperties;
import com.jujiu.agent.model.dto.request.SendMessageRequest;
import com.jujiu.agent.model.dto.response.ChatResponse;
import com.jujiu.agent.model.entity.Message;
import com.jujiu.agent.model.entity.Session;
import com.jujiu.agent.repository.MessageRepository;
import com.jujiu.agent.repository.SessionRepository;
import com.jujiu.agent.service.ChatPersistenceService;
import com.jujiu.agent.service.ChatRateLimitService;
import com.jujiu.agent.service.FunctionCallingService;
import com.jujiu.agent.service.kb.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceImplTest {

    private MessageRepository messageRepository;
    private SessionRepository sessionRepository;
    private DeepSeekClient deepSeekClient;
    private DeepSeekProperties deepSeekProperties;
    private FunctionCallingService functionCallingService;
    private ExecutorService chatExecutor;
    private ObjectMapper objectMapper;
    private ChatRateLimitService chatRateLimitService;
    private ChatPersistenceService chatPersistenceService;
    private RagService ragService;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        sessionRepository = mock(SessionRepository.class);
        deepSeekClient = mock(DeepSeekClient.class);
        deepSeekProperties = new DeepSeekProperties();
        deepSeekProperties.setSystemPrompt("你是一个测试助手");
        deepSeekProperties.setMaxContextMessages(20);
        functionCallingService = mock(FunctionCallingService.class);
        chatExecutor = mock(ExecutorService.class);
        objectMapper = new ObjectMapper();
        chatRateLimitService = mock(ChatRateLimitService.class);
        chatPersistenceService = mock(ChatPersistenceService.class);
        ragService = mock(RagService.class);

        chatService = new ChatServiceImpl(
                messageRepository,
                sessionRepository,
                deepSeekClient,
                deepSeekProperties,
                functionCallingService,
                chatExecutor,
                objectMapper,
                chatRateLimitService,
                chatPersistenceService,
                ragService
        );
    }

    @Test
    @DisplayName("开启知识增强但无可注入上下文时应继续正常聊天")
    void sendMessage_shouldContinueWhenKnowledgeContextBlank() {
        Session session = Session.builder()
                .sessionId("session_001")
                .userId(1001L)
                .title("原始标题")
                .messageCount(2)
                .lastMessage("上一次回复")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Message userMessage = Message.builder()
                .messageId("msg_user_001")
                .sessionId("session_001")
                .role("user")
                .content("帮我总结一下 ACL")
                .createdAt(LocalDateTime.now())
                .build();

        Message aiMessage = Message.builder()
                .messageId("msg_ai_001")
                .sessionId("session_001")
                .role("assistant")
                .content("这是 AI 回复")
                .createdAt(LocalDateTime.now())
                .build();

        DeepSeekResult titleResult = new DeepSeekResult();
        titleResult.setReply("测试标题");

        DeepSeekResult chatResult = new DeepSeekResult();
        chatResult.setReply("这是 AI 回复");
        chatResult.setPromptTokens(100);
        chatResult.setCompletionTokens(50);
        chatResult.setTotalTokens(150);

        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("session_001");
        request.setMessage("帮我总结一下 ACL");
        request.setEnableKnowledgeBase(true);
        request.setKnowledgeBaseId(1L);
        request.setRetrievalTopK(5);

        when(sessionRepository.selectOne(any())).thenReturn(session);
        when(chatPersistenceService.saveUserMessage("session_001", "帮我总结一下 ACL"))
                .thenReturn(userMessage);
        when(messageRepository.selectList(any())).thenReturn(List.of(userMessage));
        when(ragService.buildKnowledgeContext(1001L, 1L, "帮我总结一下 ACL", 5))
                .thenReturn("");
        when(functionCallingService.chatWithTools(eq(1001L), anyList()))
                .thenReturn(chatResult);
        when(chatPersistenceService.saveAssistantMessage("session_001", "这是 AI 回复", 50))
                .thenReturn(aiMessage);

        ChatResponse response = chatService.sendMessage(1001L, request);

        assertNotNull(response);
        assertEquals("session_001", response.getSessionId());
        assertEquals("这是 AI 回复", response.getReply());

        verify(ragService, times(1))
                .buildKnowledgeContext(1001L, 1L, "帮我总结一下 ACL", 5);
        verify(functionCallingService, times(1))
                .chatWithTools(eq(1001L), anyList());
        verify(chatPersistenceService, times(1))
                .saveAssistantMessage("session_001", "这是 AI 回复", 50);
    }
}
