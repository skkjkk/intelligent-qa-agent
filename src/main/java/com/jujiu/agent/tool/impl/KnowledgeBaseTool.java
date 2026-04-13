package com.jujiu.agent.tool.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库问答工具。
 *
 * <p>用于在 Function Calling 场景下调用知识库检索问答能力，
 * 统一复用 {@link RagService} 完成检索、Prompt 组装、模型生成与引用构造。
 *
 * <p>当前工具职责包括：
 * <ul>
 *     <li>解析工具参数</li>
 *     <li>校验问题与检索参数</li>
 *     <li>调用知识库问答服务</li>
 *     <li>将结果组织为结构化 JSON 字符串返回给模型</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Component
@Slf4j
public class KnowledgeBaseTool extends AbstractTool {
    private static final Long DEFAULT_KNOWLEDGE_BASE_ID = 1L;
    private static final Integer DEFAULT_TOP_K = 5;


    private final RagService ragService;
    private final ObjectMapper objectMapper;
    
    public KnowledgeBaseTool(RagService ragService, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取工具名称。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "knowledge_base";
    }

    /**
     * 执行知识库问答工具。
     *
     * @param params 工具参数
     * @return 工具执行结果
     */
    @Override
    public String execute(Map<String, Object> params) {

        log.info("[TOOL][KNOWLEDGE_BASE] 开始执行知识库工具 - params={}", params);

        try {
            String question = getRequiredQuestion(params);
            Long knowledgeBaseId = getKnowledgeBaseId(params);
            Integer topK = getTopK(params);

            QueryKnowledgeBaseRequest request = new QueryKnowledgeBaseRequest();
            request.setQuestion(question);
            request.setKbId(knowledgeBaseId);
            request.setTopK(topK);
            Long userId = ToolExecutionContext.getCurrentUserId();
            if (userId == null || userId <= 0) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER, "当前工具执行用户上下文缺失");
            }
            log.info("[TOOL][KNOWLEDGE_BASE] 模型主动触发知识库工具调用 - userId={}, kbId={}, topK={}, question={}",
                    userId, knowledgeBaseId, topK, question);

            log.info("[TOOL][KNOWLEDGE_BASE] 参数解析完成 - userId={}, kbId={}, topK={}, questionLength={}, rawKbId={}, rawTopK={}",
                    userId,
                    knowledgeBaseId,
                    topK,
                    question.length(),
                    params.get("knowledgeBaseId"),
                    params.get("topK"));

            KnowledgeQueryResponse response = ragService.query(userId, request);
            String result = buildToolResult(question, response);

            log.info("[TOOL][KNOWLEDGE_BASE] 知识库工具执行完成 - userId={}, kbId={}, topK={}, answerLength={}, citationCount={}",
                    userId,
                    knowledgeBaseId,
                    topK,
                    response.getAnswer() == null ? 0 : response.getAnswer().length(),
                    response.getCitations() == null ? 0 : response.getCitations().size());

            return result;
        } catch (BusinessException e) {
            log.error("[TOOL][KNOWLEDGE_BASE] 知识库工具执行失败 - code={}, message={}",
                    e.getResultCode(), e.getMessage(), e);
            return buildErrorResult(e.getMessage());
        } catch (Exception e) {
            log.error("[TOOL][KNOWLEDGE_BASE] 知识库工具执行异常", e);
            return buildErrorResult("知识库工具执行失败：" + e.getMessage());
        }
    }
    
    /**
     * 构造工具执行失败结果。
     *
     * @param errorMessage 错误信息
     * @return JSON 字符串
     */
    private String buildErrorResult(String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", errorMessage);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "知识库工具执行失败：" + errorMessage;
        }
    }

    /**
     * 构造工具执行成功结果。
     *
     * @param question 原始问题
     * @param response 知识库问答结果
     * @return JSON 字符串
     */
    private String buildToolResult(String question, KnowledgeQueryResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("question", question);
        result.put("answer", response.getAnswer());
        result.put("citations", buildCitationResult(response.getCitations()));
        result.put("hasCitations", response.getCitations() != null && !response.getCitations().isEmpty());

        try{
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[TOOL][KNOWLEDGE_BASE] 工具结果序列化失败，降级返回纯文本结果", e);
            return response.getAnswer();
        }
    }

    /**
     * 构造引用结果列表。
     *
     * @param citations 引用列表
     * @return 结构化引用结果
     */
    private List<Map<String, Object>> buildCitationResult(List<CitationResponse> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }

        return citations.stream()
                .map(citation -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("documentTitle", citation.getDocumentTitle());
                    item.put("snippet", citation.getSnippet());
                    item.put("score", citation.getScore());
                    item.put("rank", citation.getRank());
                    return item;
                })
                .toList();
    }
    
    /**
     * 获取检索数量参数。
     *
     * @param params 工具参数
     * @return 检索数量
     */
    private Integer getTopK(Map<String, Object> params) {
        Object topKObj = params.get("topK");
        if (topKObj == null) {
            return DEFAULT_TOP_K;
        }

        String rawValue = String.valueOf(topKObj).trim();
        if (rawValue.isEmpty() || "null".equalsIgnoreCase(rawValue)) {
            return DEFAULT_TOP_K;
        }

        try {
            int topK;
            if (topKObj instanceof Number number) {
                topK = number.intValue();
            } else if (rawValue.contains(".")) {
                topK = (int) Double.parseDouble(rawValue);
            } else {
                topK = Integer.parseInt(rawValue);
            }

            if (topK <= 0) {
                return DEFAULT_TOP_K;
            }

            return topK;
        } catch (Exception e) {
            log.warn("[TOOL][KNOWLEDGE_BASE] topK 解析失败，回退默认值 - rawValue={}", rawValue, e);
            return DEFAULT_TOP_K;
        }
    }

    /**
     * 获取知识库 ID 参数。
     *
     * @param params 工具参数
     * @return 知识库 ID
     */
    private Long getKnowledgeBaseId(Map<String, Object> params) {
        Object kbIdObj = params.get("knowledgeBaseId");
        if (kbIdObj == null) {
            return DEFAULT_KNOWLEDGE_BASE_ID;
        }
        
        String rawValue = String.valueOf(kbIdObj).trim();
        if (rawValue.isEmpty() || "null".equalsIgnoreCase(rawValue)) {
            return DEFAULT_KNOWLEDGE_BASE_ID;
        }
        
        try{
            long kbId;
            if (kbIdObj instanceof Number number) {
                kbId = number.longValue();
            } else if (rawValue.contains(".")) {
                kbId = (long) Double.parseDouble(rawValue);
            } else {
                kbId = Long.parseLong(rawValue);
            }
            if (kbId <= 0) {
                return DEFAULT_KNOWLEDGE_BASE_ID;
            }

            return kbId;
        }catch (Exception e){
            log.warn("[TOOL][KNOWLEDGE_BASE] knowledgeBaseId 解析失败，回退默认值 - rawValue={}", rawValue, e);
            return DEFAULT_KNOWLEDGE_BASE_ID;
        }
    }

    /**
     * 获取必填问题参数。
     *
     * @param params 工具参数
     * @return 用户问题
     */
    private String getRequiredQuestion(Map<String, Object> params) {
        Object questionObj = params.get("question");
        if (questionObj == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }

        String question = String.valueOf(questionObj).trim();
        if (question.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
         }
        return question;
    }

    /**
     * 获取工具参数定义。
     *
     * @return 参数定义
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "string");
        question.put("description", "用户问题");

        Map<String, Object> knowledgeBaseId = new LinkedHashMap<>();
        knowledgeBaseId.put("type", "integer");
        knowledgeBaseId.put("description", "知识库ID，可选，默认使用主知识库");

        Map<String, Object> topK = new LinkedHashMap<>();
        topK.put("type", "integer");
        topK.put("description", "检索结果数量，可选，默认 5");
        
        properties.put("question", question);
        properties.put("knowledgeBaseId", knowledgeBaseId);
        properties.put("topK", topK);

        parameters.put("properties", properties);
        parameters.put("required", List.of("question"));
        
        return parameters;
    }
    
    
}
