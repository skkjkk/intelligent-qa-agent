package com.jujiu.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.chat.infrastructure.config.DeepSeekProperties;
import com.jujiu.agent.module.chat.api.request.SendMessageRequest;
import com.jujiu.agent.module.chat.api.response.ChatResponse;
import com.jujiu.agent.module.chat.domain.entity.Message;
import com.jujiu.agent.module.chat.domain.entity.Session;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmClientRouter;
import com.jujiu.agent.module.chat.infrastructure.llm.LlmResult;
import com.jujiu.agent.module.chat.infrastructure.mapper.MessageMapper;
import com.jujiu.agent.module.chat.infrastructure.mapper.SessionMapper;
import com.jujiu.agent.module.chat.application.service.ChatPersistenceService;
import com.jujiu.agent.module.chat.application.service.ChatRateLimitService;
import com.jujiu.agent.module.chat.application.service.FunctionCallingService;
import com.jujiu.agent.module.chat.application.service.impl.ChatServiceImpl;
import com.jujiu.agent.module.kb.application.service.RagService;
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

    private MessageMapper messageMapper;
    private SessionMapper sessionMapper;
    private DeepSeekProperties deepSeekProperties;
    private LlmClientRouter llmClientRouter;
    private FunctionCallingService functionCallingService;
    private ExecutorService chatExecutor;
    private ObjectMapper objectMapper;
    private ChatRateLimitService chatRateLimitService;
    private ChatPersistenceService chatPersistenceService;
    private RagService ragService;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        sessionMapper = mock(SessionMapper.class);
        deepSeekProperties = new DeepSeekProperties();
        deepSeekProperties.setSystemPrompt("你是一个测试助手");
        deepSeekProperties.setMaxContextMessages(20);
        llmClientRouter = mock(LlmClientRouter.class);
        functionCallingService = mock(FunctionCallingService.class);
        chatExecutor = mock(ExecutorService.class);
        objectMapper = new ObjectMapper();
        chatRateLimitService = mock(ChatRateLimitService.class);
        chatPersistenceService = mock(ChatPersistenceService.class);
        ragService = mock(RagService.class);

        chatService = new ChatServiceImpl(
                messageMapper,
                sessionMapper,
                deepSeekProperties,
                llmClientRouter,
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

        LlmResult chatResult = new LlmResult();
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

        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(chatPersistenceService.saveUserMessage("session_001", "帮我总结一下 ACL"))
                .thenReturn(userMessage);
        when(messageMapper.selectList(any())).thenReturn(List.of(userMessage));
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

    @Test
    @DisplayName("开启知识增强且有上下文时也应正常聊天")
    void sendMessage_shouldContinueWhenKnowledgeContextPresent() {
        Session session = Session.builder()
                .sessionId("session_002")
                .userId(1001L)
                .title("原始标题")
                .messageCount(2)
                .lastMessage("上一次回复")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Message userMessage = Message.builder()
                .messageId("msg_user_002")
                .sessionId("session_002")
                .role("user")
                .content("ACL 怎么设计")
                .createdAt(LocalDateTime.now())
                .build();

        Message aiMessage = Message.builder()
                .messageId("msg_ai_002")
                .sessionId("session_002")
                .role("assistant")
                .content("这是 AI 回复")
                .createdAt(LocalDateTime.now())
                .build();

        LlmResult chatResult = new LlmResult();
        chatResult.setReply("这是 AI 回复");
        chatResult.setPromptTokens(120);
        chatResult.setCompletionTokens(60);
        chatResult.setTotalTokens(180);

        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("session_002");
        request.setMessage("ACL 怎么设计");
        request.setEnableKnowledgeBase(true);
        request.setKnowledgeBaseId(1L);
        request.setRetrievalTopK(5);

        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(chatPersistenceService.saveUserMessage("session_002", "ACL 怎么设计"))
                .thenReturn(userMessage);
        when(messageMapper.selectList(any())).thenReturn(List.of(userMessage));
        when(ragService.buildKnowledgeContext(1001L, 1L, "ACL 怎么设计", 5))
                .thenReturn("这是知识库上下文");
        when(functionCallingService.chatWithTools(eq(1001L), anyList()))
                .thenReturn(chatResult);
        when(chatPersistenceService.saveAssistantMessage("session_002", "这是 AI 回复", 60))
                .thenReturn(aiMessage);

        ChatResponse response = chatService.sendMessage(1001L, request);

        assertNotNull(response);
        assertEquals("session_002", response.getSessionId());
        assertEquals("这是 AI 回复", response.getReply());

        verify(ragService, times(1))
                .buildKnowledgeContext(1001L, 1L, "ACL 怎么设计", 5);
        verify(functionCallingService, times(1))
                .chatWithTools(eq(1001L), anyList());
    }

    @Test
    @DisplayName("未开启知识增强时不应调用知识库上下文构造")
    void sendMessage_shouldNotCallKnowledgeContextWhenKnowledgeEnhanceDisabled() {
        Session session = Session.builder()
                .sessionId("session_003")
                .userId(1001L)
                .title("原始标题")
                .messageCount(2)
                .lastMessage("上一次回复")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Message userMessage = Message.builder()
                .messageId("msg_user_003")
                .sessionId("session_003")
                .role("user")
                .content("普通聊天问题")
                .createdAt(LocalDateTime.now())
                .build();

        Message aiMessage = Message.builder()
                .messageId("msg_ai_003")
                .sessionId("session_003")
                .role("assistant")
                .content("普通聊天回复")
                .createdAt(LocalDateTime.now())
                .build();

        LlmResult chatResult = new LlmResult();
        chatResult.setReply("普通聊天回复");
        chatResult.setPromptTokens(80);
        chatResult.setCompletionTokens(40);
        chatResult.setTotalTokens(120);

        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId("session_003");
        request.setMessage("普通聊天问题");
        request.setEnableKnowledgeBase(false);

        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(chatPersistenceService.saveUserMessage("session_003", "普通聊天问题"))
                .thenReturn(userMessage);
        when(messageMapper.selectList(any())).thenReturn(List.of(userMessage));
        when(functionCallingService.chatWithTools(eq(1001L), anyList()))
                .thenReturn(chatResult);
        when(chatPersistenceService.saveAssistantMessage("session_003", "普通聊天回复", 40))
                .thenReturn(aiMessage);

        ChatResponse response = chatService.sendMessage(1001L, request);

        assertNotNull(response);
        assertEquals("普通聊天回复", response.getReply());

        verifyNoInteractions(ragService);
        verify(functionCallingService, times(1))
                .chatWithTools(eq(1001L), anyList());
    }
}
