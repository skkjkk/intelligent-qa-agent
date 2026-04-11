package com.jujiu.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.tool.ToolExecutionContext;
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

/**
 * 知识库工具单元测试。
 *
 * <p>用于验证知识库工具的参数解析、默认值兜底、
 * RAG 服务调用以及结构化结果返回逻辑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public class KnowledgeBaseToolTest {
    private RagService ragService;
    private ObjectMapper objectMapper;
    private KnowledgeBaseTool knowledgeBaseTool;

    @BeforeEach
    void setUp(){
        ragService = mock(RagService.class);
        objectMapper = new ObjectMapper();
        knowledgeBaseTool = new KnowledgeBaseTool(ragService, objectMapper);

        ToolExecutionContext.setCurrentUserId(1001L);
    }

    @Test
    @DisplayName("question缺失时用返回错误结果")
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
    void shouldParseKnowledgeBaseIdFromDecimalString() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("question", "这个项目的改造清单包括哪些内容");
        params.put("knowledgeBaseId", "1.0");
        params.put("topK", 3);

        when(ragService.query(eq(1001L), any(QueryKnowledgeBaseRequest.class)))
                .thenReturn(buildSuccessResponse());

        knowledgeBaseTool.execute(params);

        ArgumentCaptor<QueryKnowledgeBaseRequest> requestCaptor =
                ArgumentCaptor.forClass(QueryKnowledgeBaseRequest.class);

        verify(ragService, times(1)).query(eq(1001L), requestCaptor.capture());

        QueryKnowledgeBaseRequest actualRequest = requestCaptor.getValue();
        assertEquals("这个项目的改造清单包括哪些内容", actualRequest.getQuestion());
        assertEquals(1L, actualRequest.getKbId());
        assertEquals(3, actualRequest.getTopK());
    }
    @Test
    @DisplayName("topK 非法时应回退默认值 5")
    void shouldFallbackToDefaultTopKWhenInvalid() {
        Map<String, Object> params = new HashMap<>();
        params.put("question", "这个项目的改造清单包括哪些内容");
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
        params.put("question", "这个项目的改造清单包括哪些内容");
        params.put("knowledgeBaseId", 1);
        params.put("topK", 5);

        when(ragService.query(eq(1001L), any(QueryKnowledgeBaseRequest.class)))
                .thenReturn(buildSuccessResponse());

        String result = knowledgeBaseTool.execute(params);

        JsonNode jsonNode = objectMapper.readTree(result);
        assertEquals("这个项目的改造清单包括哪些内容", jsonNode.get("question").asText());
        assertEquals("这是知识库回答内容", jsonNode.get("answer").asText());
        assertTrue(jsonNode.get("citations").isArray());
        assertEquals(1, jsonNode.get("citations").size());
        assertEquals("改造清单文档", jsonNode.get("citations").get(0).get("documentTitle").asText());
    }

    /**
     * 构造成功的知识库问答响应。
     *
     * @return 知识库问答响应
     */
    private KnowledgeQueryResponse buildSuccessResponse() {
        return KnowledgeQueryResponse.builder()
                .answer("这是知识库回答内容")
                .citations(List.of(
                        CitationResponse.builder()
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
