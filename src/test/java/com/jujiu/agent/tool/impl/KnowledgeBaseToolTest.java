package com.jujiu.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.kb.api.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.module.kb.api.response.CitationResponse;
import com.jujiu.agent.module.kb.api.response.KnowledgeQueryResponse;
import com.jujiu.agent.module.kb.application.service.RagService;
import com.jujiu.agent.module.tool.runtime.ToolExecutionContext;
import com.jujiu.agent.module.tool.runtime.impl.KnowledgeBaseTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeBaseToolTest {

    private RagService ragService;
    private ObjectMapper objectMapper;
    private KnowledgeBaseTool knowledgeBaseTool;

    @BeforeEach
    void setUp() {
        ragService = mock(RagService.class);
        objectMapper = new ObjectMapper();
        knowledgeBaseTool = new KnowledgeBaseTool(ragService, objectMapper);

        ToolExecutionContext.setCurrentUserId(1001L);
    }

    @AfterEach
    void tearDown() {
        ToolExecutionContext.clear();
    }

    @Test
    @DisplayName("question 缺失时应返回错误结果")
    void shouldReturnErrorWhenQuestionMissing() throws Exception {
        Map<String, Object> params = new HashMap<>();

        String result = knowledgeBaseTool.execute(params);

        JsonNode jsonNode = objectMapper.readTree(result);
        assertFalse(jsonNode.get("success").asBoolean());
        assertEquals("question 不能为空", jsonNode.get("error").asText());

        verifyNoInteractions(ragService);
    }

    @Test
    @DisplayName("knowledgeBaseId 为 1.0 时应正确解析为 1")
    void shouldParseKnowledgeBaseIdFromDecimalString() {
        Map<String, Object> params = new HashMap<>();
        params.put("question", "这个项目的 ACL 改造清单包括哪些内容");
        params.put("knowledgeBaseId", "1.0");
        params.put("topK", 3);

        when(ragService.query(eq(1001L), any(QueryKnowledgeBaseRequest.class)))
                .thenReturn(buildSuccessResponse());

        knowledgeBaseTool.execute(params);

        ArgumentCaptor<QueryKnowledgeBaseRequest> requestCaptor =
                ArgumentCaptor.forClass(QueryKnowledgeBaseRequest.class);

        verify(ragService, times(1)).query(eq(1001L), requestCaptor.capture());

        QueryKnowledgeBaseRequest actualRequest = requestCaptor.getValue();
        assertEquals("这个项目的 ACL 改造清单包括哪些内容", actualRequest.getQuestion());
        assertEquals(1L, actualRequest.getKbId());
        assertEquals(3, actualRequest.getTopK());
    }

    @Test
    @DisplayName("topK 非法时应回退默认值 5")
    void shouldFallbackToDefaultTopKWhenInvalid() {
        Map<String, Object> params = new HashMap<>();
        params.put("question", "这个项目的 ACL 改造清单包括哪些内容");
        params.put("knowledgeBaseId", 1);
        params.put("topK", "abc");

        when(ragService.query(eq(1001L), any(QueryKnowledgeBaseRequest.class)))
                .thenReturn(buildSuccessResponse());

        knowledgeBaseTool.execute(params);

        ArgumentCaptor<QueryKnowledgeBaseRequest> requestCaptor =
                ArgumentCaptor.forClass(QueryKnowledgeBaseRequest.class);

        verify(ragService, times(1)).query(eq(1001L), requestCaptor.capture());

        QueryKnowledgeBaseRequest actualRequest = requestCaptor.getValue();
        assertEquals(5, actualRequest.getTopK());
    }

    @Test
    @DisplayName("RAG 查询成功时应返回结构化 JSON 结果")
    void shouldReturnStructuredJsonWhenQuerySuccess() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("question", "这个项目的 ACL 改造清单包括哪些内容");
        params.put("knowledgeBaseId", 1);
        params.put("topK", 5);

        when(ragService.query(eq(1001L), any(QueryKnowledgeBaseRequest.class)))
                .thenReturn(buildSuccessResponse());

        String result = knowledgeBaseTool.execute(params);

        JsonNode jsonNode = objectMapper.readTree(result);
        assertTrue(jsonNode.get("success").asBoolean());
        assertEquals("这个项目的 ACL 改造清单包括哪些内容", jsonNode.get("question").asText());
        assertEquals("这是知识库回答内容", jsonNode.get("answer").asText());
        assertTrue(jsonNode.get("citations").isArray());
        assertEquals(1, jsonNode.get("citations").size());
        assertEquals("改造清单文档", jsonNode.get("citations").get(0).get("documentTitle").asText());
    }

    @Test
    @DisplayName("ACL 过滤后无引用时也应返回 success=true")
    void shouldReturnSuccessWhenQueryHasNoCitations() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("question", "没有权限看到的知识会怎样");
        params.put("knowledgeBaseId", 1);
        params.put("topK", 5);

        when(ragService.query(eq(1001L), any(QueryKnowledgeBaseRequest.class)))
                .thenReturn(KnowledgeQueryResponse.builder()
                        .answer("抱歉，知识库中没有足够信息支持回答该问题。")
                        .citations(List.of())
                        .promptTokens(0)
                        .completionTokens(0)
                        .totalTokens(0)
                        .latencyMs(100L)
                        .build());

        String result = knowledgeBaseTool.execute(params);

        JsonNode jsonNode = objectMapper.readTree(result);
        assertTrue(jsonNode.get("success").asBoolean());
        assertEquals("没有权限看到的知识会怎样", jsonNode.get("question").asText());
        assertEquals("抱歉，知识库中没有足够信息支持回答该问题。", jsonNode.get("answer").asText());
        assertTrue(jsonNode.get("citations").isArray());
        assertEquals(0, jsonNode.get("citations").size());

        if (jsonNode.has("hasCitations")) {
            assertFalse(jsonNode.get("hasCitations").asBoolean());
        }
    }

    @Test
    @DisplayName("缺少用户上下文时应返回错误结果")
    void shouldReturnErrorWhenUserContextMissing() throws Exception {
        ToolExecutionContext.clear();

        Map<String, Object> params = new HashMap<>();
        params.put("question", "ACL 是什么");
        params.put("knowledgeBaseId", 1);
        params.put("topK", 5);

        String result = knowledgeBaseTool.execute(params);

        JsonNode jsonNode = objectMapper.readTree(result);
        assertFalse(jsonNode.get("success").asBoolean());
        assertTrue(jsonNode.get("error").asText().contains("当前工具执行用户上下文缺失"));

        verifyNoInteractions(ragService);
    }

    private KnowledgeQueryResponse buildSuccessResponse() {
        return KnowledgeQueryResponse.builder()
                .answer("这是知识库回答内容")
                .citations(List.of(
                        CitationResponse.builder()
                                .chunkId(11L)
                                .documentId(101L)
                                .documentTitle("改造清单文档")
                                .snippet("这是引用片段")
                                .score(0.95D)
                                .rank(1)
                                .build()
                ))
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .latencyMs(1200L)
                .build();
    }
}
