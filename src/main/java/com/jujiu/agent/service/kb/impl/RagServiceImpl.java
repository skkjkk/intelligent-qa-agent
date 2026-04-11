package com.jujiu.agent.service.kb.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.client.DeepSeekClient;
import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.model.entity.KbRetrievalTrace;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.repository.KbRetrievalTraceRepository;
import com.jujiu.agent.service.kb.QueryLogService;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.service.kb.VectorSearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * RAG 问答服务实现。
 *
 * <p>当前类是最小 RAG 闭环的核心编排入口，
 * 后续将统一承接知识库问答 API、Tool 调用和聊天增强能力。
 *
 * <p>当前阶段职责包括：
 * <ul>
 *     <li>接收请求并兜底关键参数</li>
 *     <li>调用检索服务获取分块结果</li>
 *     <li>构造知识库上下文</li>
 *     <li>调用 DeepSeekClient 生成答案</li>
 *     <li>封装引用结果并返回</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {

    private static final String DEFAULT_RETRIEVAL_MODE = "VECTOR";
    private static final String DEFAULT_QUERY_SOURCE = "KB_API";
    private static final String DEFAULT_QUERY_STATUS_SUCCESS = "SUCCESS";
    private static final String DEFAULT_QUERY_STATUS_EMPTY = "EMPTY";
    private static final String DEFAULT_QUERY_STATUS_FAILED = "FAILED";
    private static final int STREAM_FLUSH_MIN_LENGTH = 32;

    private final VectorSearchService vectorSearchService;
    private final DeepSeekClient deepSeekClient;
    private final KbQueryLogRepository kbQueryLogRepository;
    private final KbRetrievalTraceRepository kbRetrievalTraceRepository;
    private final KnowledgeBaseProperties  knowledgeBaseProperties;
    private final ExecutorService chatExecutor;
    public final ObjectMapper objectMapper;
    private final QueryLogService queryLogService;
    public RagServiceImpl(VectorSearchService vectorSearchService,
                          DeepSeekClient deepSeekClient,
                          KbQueryLogRepository kbQueryLogRepository,
                          KbRetrievalTraceRepository kbRetrievalTraceRepository,
                          KnowledgeBaseProperties knowledgeBaseProperties,
                          ObjectMapper objectMapper,
                          ExecutorService chatExecutor,
                          QueryLogService queryLogService) {
        this.vectorSearchService = vectorSearchService;
        this.deepSeekClient = deepSeekClient;
        this.kbQueryLogRepository = kbQueryLogRepository;
        this.kbRetrievalTraceRepository = kbRetrievalTraceRepository;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.objectMapper = objectMapper;
        this.chatExecutor = chatExecutor;
        this.queryLogService = queryLogService;
    }

    /**
     * 执行知识库问答。
     *
     * @param userId 当前用户 ID
     * @param request 知识库问答请求
     * @return 知识库问答响应结果
     */
    @Override
    @Transactional
    public KnowledgeQueryResponse query(Long userId, QueryKnowledgeBaseRequest request) {
        validateRequest(userId, request);
        
        long startTime = System.currentTimeMillis();
        Long kbId = request.getKbId() == null ? 1L : request.getKbId();
        Integer topK = request.getTopK() == null ? 5 : request.getTopK();

        log.info("[KB][QUERY] 开始执行知识库问答 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, request.getQuestion().length()
        );

        // 检索
        List<ChunkSearchResult> searchResults = vectorSearchService.search(
                kbId, 
                userId, 
                request.getQuestion(), 
                topK);

        if (searchResults == null || searchResults.isEmpty()) {
            return handleEmptyResult(userId, kbId, request, topK, startTime);
        }

        // 构造引用列表
        List<CitationResponse> citations = buildCitation(searchResults);
        // 构造知识库上下文
        String context = buildContext(searchResults);
        // 构造提示
        List<DeepSeekMessage> messages = buildPromptMessages(request.getQuestion(), context);

        log.info("[KB][QUERY] 检索结果已完成组装，开始调用模型生成答案 - kbId={}, userId={}, citationCount={}",
                kbId, userId, citations.size());
        
        // 调用 DeepSeek
        DeepSeekResult deepSeekResult = deepSeekClient.chat(messages);

        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[KB][QUERY] 知识库问答完成 - kbId={}, userId={}, topK={}, questionLength={}, latencyMs={}",
                kbId, userId, topK, request.getQuestion().length(), latencyMs
        );

        // 保存查询日志
        KbQueryLog queryLog = queryLogService.saveQueryLog(
                userId,
                kbId,
                request,
                topK,
                deepSeekResult,
                citations,
                latencyMs,
                DEFAULT_QUERY_STATUS_SUCCESS,
                null
        );

        // 保存检索轨迹
        queryLogService.saveRetrievalTrace(queryLog.getId(), searchResults);

        // 封装响应结果
        return KnowledgeQueryResponse.builder()
                .answer(deepSeekResult.getReply())
                .citations(citations)
                .promptTokens(deepSeekResult.getPromptTokens())
                .completionTokens(deepSeekResult.getCompletionTokens())
                .totalTokens(deepSeekResult.getTotalTokens())
                .latencyMs(latencyMs)
                .build();
    }

    /**
     * 构造知识库增强上下文。
     *
     * <p>该方法用于聊天增强场景，只负责检索知识片段并拼接上下文，
     * 不负责直接调用模型生成最终答案。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param question 用户问题
     * @param topK 检索数量
     * @return 知识库上下文文本，未命中时返回空字符串
     */
    @Override
    public String buildKnowledgeContext(Long userId, Long kbId, String question, Integer topK) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }
        Long targetKbId = kbId == null ? 1L : kbId;
        Integer targetTopK = (topK == null || topK <= 0) ? 5 : topK;
        
        log.info("[KB][CONTEXT] 开始构造知识上下文 - kbId={}, userId={}, topK={}, questionLength={}",
                targetKbId, userId, targetTopK, question.length());

        List<ChunkSearchResult> searchResults = vectorSearchService.search(
                targetKbId,
                userId,
                question,
                targetTopK);
        
        if (searchResults == null || searchResults.isEmpty()) {
            log.info("[KB][CONTEXT] 未检索到可用知识片段 - kbId={}, userId={}", targetKbId, userId);
            return "";
        }
        
        String context = buildContext(searchResults);

        log.info("[KB][CONTEXT] 知识上下文构造完成 - kbId={}, userId={}, resultCount={}, contextLength={}",
                targetKbId, userId, searchResults.size(), context.length());
        
        return context;
    }

    /**
     * 执行知识库流式问答。
     *
     * <p>当前实现先同步完成检索、引用与 Prompt 组装，
     * 再调用 DeepSeek 流式接口逐段返回模型生成内容。
     *
     * @param userId 当前用户 ID
     * @param request 知识库问答请求
     * @return SSE 发射器
     */
    @Override
    public SseEmitter queryStream(Long userId, QueryKnowledgeBaseRequest request) {
        validateRequest(userId, request);
        
        SseEmitter emitter = new SseEmitter(0L);

        chatExecutor.submit(()-> {

            long startTime = System.currentTimeMillis();
            Long kbId = request.getKbId() == null ? 1L : request.getKbId();
            Integer topK = request.getTopK() == null ? 5 : request.getTopK();

            try {
                log.info("[KB][QUERY][STREAM] 开始执行知识库流式问答 - kbId={}, userId={}, topK={}, questionLength={}",
                        kbId, userId, topK, request.getQuestion().length());

                List<ChunkSearchResult> searchResults = vectorSearchService.search(
                        kbId,
                        userId,
                        request.getQuestion(),
                        topK
                );

                if (searchResults == null || searchResults.isEmpty()) {
                    log.info("[KB][QUERY][STREAM] 未检索到可用结果 - kbId={}, userId={}", kbId, userId);

                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("抱歉，知识库中没有足够信息支持回答该问题。"));

                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("STREAM_FINISHED"));
                    emitter.complete();
                    return;
                }

                List<CitationResponse> citations = buildCitation(searchResults);
                String context = buildContext(searchResults);
                List<DeepSeekMessage> messages = buildPromptMessages(request.getQuestion(), context);

                log.info("[KB][QUERY][STREAM] 检索结果已完成组装，开始流式调用模型 - kbId={}, userId={}, citationCount={}",
                        kbId, userId, citations.size());

                StringBuilder answerBuilder = new StringBuilder();
                StringBuilder chunkBuffer = new StringBuilder();
                int pushCount = 0;
                
                for(String content : deepSeekClient.chatStream(messages).toIterable()) {
                    if (content == null || content.isEmpty()) {
                        return;
                    }

                    answerBuilder.append(content);
                    chunkBuffer.append(content);

                    if (shouldFlushStreamChunk(chunkBuffer)) {
                        String output = chunkBuffer.toString();
                        pushCount++;

                        log.info("[KB][QUERY][STREAM] 推送流式片段 - kbId={}, userId={}, pushCount={}, chunkLength={}, totalLength={}",
                                kbId, userId, pushCount, output.length(), answerBuilder.length());

                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(output));

                        chunkBuffer.setLength(0);
                    }
                }
                if (!chunkBuffer.isEmpty()) {
                    String output = chunkBuffer.toString();
                    pushCount++;

                    log.info("[KB][QUERY][STREAM] 推送最后流式片段 - kbId={}, userId={}, pushCount={}, chunkLength={}, totalLength={}",
                            kbId, userId, pushCount, output.length(), answerBuilder.length());

                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(output));
                }

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("STREAM_FINISHED"));

                emitter.complete();

                long latencyMs = System.currentTimeMillis() - startTime;
                log.info("[KB][QUERY][STREAM] 知识库流式问答完成 - kbId={}, userId={}, answerLength={}, latencyMs={}",
                        kbId, userId, answerBuilder.length(), latencyMs);
                
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - startTime;
                log.error("[KB][QUERY][STREAM] 知识库流式问答失败 - kbId={}, userId={}, latencyMs={}",
                        kbId, userId, latencyMs, e);
                
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("知识库流式问答失败：" + e.getMessage())
                    );
                } catch (Exception sendException) {
                    log.warn("[KB][QUERY][STREAM] 推送错误事件失败 - kbId={}, userId={}", kbId, userId, sendException);
                }
                
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * 判断当前流式缓冲内容是否应当立即输出。
     *
     * <p>当前策略：
     * <ul>
     *     <li>缓冲长度达到最小阈值时输出</li>
     *     <li>遇到换行或常见中文句读时提前输出</li>
     * </ul>
     *
     * @param buffer 当前缓冲内容
     * @return true 表示应立即输出
     */
    private boolean shouldFlushStreamChunk(StringBuilder buffer) {
        if (buffer == null || buffer.length() == 0) {
            return false;
        }

        if (buffer.length() >= STREAM_FLUSH_MIN_LENGTH) {
            return true;
        }

        char lastChar = buffer.charAt(buffer.length() - 1);
        return lastChar == '\n'
                || lastChar == '。'
                || lastChar == '！'
                || lastChar == '？'
                || lastChar == '；'
                || lastChar == '：';
    }

    /**
     * 保存检索轨迹。
     *
     * @param queryLogId 查询日志 ID
     * @param searchResults 检索结果
     */
    private void saveRetrievalTrace(Long queryLogId, List<ChunkSearchResult> searchResults) {
        for (ChunkSearchResult searchResult : searchResults) {
            KbRetrievalTrace result = KbRetrievalTrace.builder()
                    .queryLogId(queryLogId)
                    .chunkId(searchResult.getChunkId())
                    .score(searchResult.getScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(searchResult.getScore()))
                    .rankNo(searchResult.getRank())
                    .retrievalType(DEFAULT_RETRIEVAL_MODE)
                    .createdAt(LocalDateTime.now())
                    .build();
            kbRetrievalTraceRepository.insert(result);
        }
    }

    /**
     * 保存知识库查询日志。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param request 原始请求
     * @param topK 检索数量
     * @param deepSeekResult 模型调用结果
     * @param citations 引用列表
     * @param latencyMs 总耗时
     * @param status 查询状态
     * @param errorMessage 错误信息
     * @return 查询日志实体
     */
    private KbQueryLog saveQueryLog(Long userId,
                                    Long kbId,
                                    QueryKnowledgeBaseRequest request,
                                    Integer topK,
                                    DeepSeekResult deepSeekResult,
                                    List<CitationResponse> citations,
                                    long latencyMs,
                                    String status,
                                    String errorMessage) {

        KbQueryLog queryLog = KbQueryLog.builder()
                .kbId(kbId)
                .userId(userId)
                .sessionId(null)
                .querySource(DEFAULT_QUERY_SOURCE)
                .question(request.getQuestion())
                .rewrittenQuestion(null)
                .answer(deepSeekResult != null ? deepSeekResult.getReply() : "知识库中没有足够信息支持回答该问题。")
                .retrievalTopK(topK)
                .retrievalMode(DEFAULT_RETRIEVAL_MODE)
                .citedChunkIds(toCitedChunkIdsJson(citations))
                .promptTokens(deepSeekResult != null ? deepSeekResult.getPromptTokens() : 0)
                .completionTokens(deepSeekResult != null ? deepSeekResult.getCompletionTokens() : 0)
                .totalTokens(deepSeekResult != null ? deepSeekResult.getTotalTokens() : 0)
                .latencyMs(Math.toIntExact(latencyMs))
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();
        kbQueryLogRepository.insert(queryLog);
        
        return queryLog;
    }

    /**
     * 将引用中的 chunkId 列表转换为 JSON 字符串。
     *
     * @param citations 引用列表
     * @return JSON 字符串
     */
    private String toCitedChunkIdsJson(List<CitationResponse> citations) {
        List<Long> chunkIds = citations.stream()
                .map(CitationResponse::getChunkId)
                .toList();
        try {
            return objectMapper.writeValueAsString(chunkIds);
        } catch (JsonProcessingException e) {
            log.error("[KB][QUERY] 引用 chunkId 转换为 JSON 序列化失败 - chunkIds={}", chunkIds, e);
            return "[]";
        }
    }

    /**
     * 构造知识库问答消息列表。
     *
     * @param question 用户问题
     * @param context 检索上下文
     * @return DeepSeek 消息列表
     */
    private List<DeepSeekMessage> buildPromptMessages(@NotBlank(message = "问题不能为空") 
                                                      @Size(max = 2000, message = "问题长度不能超过2000字符") 
                                                      String question, 
                                                      String context) {
        String prompt = buildPrompt(question, context);
        DeepSeekMessage userMessage = new DeepSeekMessage();
        userMessage.setRole(DeepSeekMessage.MessageRole.USER);
        userMessage.setContent(prompt);
        return List.of(userMessage);
    }

    /**
     * 构造知识库问答 Prompt。
     *
     * <p>优先读取配置文件中的 Prompt 模板，并替换上下文与问题占位符。
     * 当配置缺失时，回退到默认模板。
     *
     * @param question 用户问题
     * @param context 检索上下文
     * @return 最终 Prompt
     */
    private String buildPrompt(String question, String context) {
        String template = getPromptTemplate();
        String prompt = template
                .replace("{{context}}", context == null ? "" : context)
                .replace("{{question}}", question == null ? "" : question);

        log.info("[KB][QUERY] Prompt 构造完成 - questionLength={}, contextLength={}, promptLength={}",
                question == null ? 0 : question.length(),
                context == null ? 0 : context.length(),
                prompt.length());
        return prompt;
    }

    private String getPromptTemplate() {
        if (knowledgeBaseProperties != null 
                && knowledgeBaseProperties.getRag() != null 
                && knowledgeBaseProperties.getRag().getPromptTemplate() != null
                && !knowledgeBaseProperties.getRag().getPromptTemplate().isBlank()){
            return knowledgeBaseProperties.getRag().getPromptTemplate();
        }
        
        return """
            你是一个知识库问答助手。请严格基于参考资料回答问题。
            如果参考资料不足，请明确说“知识库中没有足够信息支持回答该问题”。

            【参考资料】
            {{context}}

            【问题】
            {{question}}

            【回答要求】
            1. 仅基于参考资料回答
            2. 使用中文
            3. 不要编造内容
            4. 如果引用资料，可标注[1][2]
            """;
    }

    /**
     * 构造知识库上下文。
     *
     * <p>当前采用编号拼接方式组织上下文，
     * 便于模型引用与前端溯源。
     *
     * @param searchResults 检索结果
     * @return 上下文文本
     */
    private String buildContext(List<ChunkSearchResult> searchResults) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            ChunkSearchResult searchResult = searchResults.get(i);
            builder.append("[").append(i + 1).append("] ")
                    .append(searchResult.getDocumentTitle())
                    .append("\n")
                    .append(searchResult.getContent())
                    .append("\n\n");
        }
        String context = builder.toString();
        
        log.info("[KB][QUERY] 上下文构造完成 - resultCount={}, contextLength={}",
                searchResults == null ? 0 : searchResults.size(), context.length());

        return context;
    }

    /**
     * 处理零命中场景。
     *
     * @param userId 当前用户 ID
     * @param kbId 知识库 ID
     * @param request 原始请求
     * @param topK 检索数量
     * @param startTime 开始时间
     * @return 问答响应
     */
    private KnowledgeQueryResponse handleEmptyResult(Long userId, 
                                                     Long kbId, 
                                                     QueryKnowledgeBaseRequest request, 
                                                     Integer topK, 
                                                     Long startTime) {
        long latencyMs = System.currentTimeMillis() - startTime;
        String answer = "抱歉，知识库中没有足够信息支持回答该问题。";

        queryLogService.saveQueryLog(
                userId,
                kbId,
                request,
                topK,
                null,
                List.of(),
                latencyMs,
                DEFAULT_QUERY_STATUS_EMPTY,
                null
        );

        return KnowledgeQueryResponse.builder()
                .answer(answer)
                .citations(List.of())
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .latencyMs(latencyMs)
                .build();
    }

    /**
     * 构造引用列表。
     *
     * @param searchResults 检索结果
     * @return 引用响应列表
     */
    private List<CitationResponse> buildCitation(List<ChunkSearchResult> searchResults) {
        List<CitationResponse> citations = new ArrayList<>();
        for (ChunkSearchResult searchResult : searchResults) {
            citations.add(
                    CitationResponse.builder()
                            .chunkId(searchResult.getChunkId())
                            .documentId(searchResult.getDocumentId())
                            .documentTitle(searchResult.getDocumentTitle())
                            .snippet(searchResult.getContent())
                            .score(searchResult.getScore())
                            .rank(searchResult.getRank())
                            .build()
            );
        }
        
        log.info("[KB][QUERY] 引用构造完成 - citationCount={}", citations.size());

        return citations;
    }
    private void validateRequest(Long userId, QueryKnowledgeBaseRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "request 不能为空");
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }
    }
}
