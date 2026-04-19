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
import com.jujiu.agent.model.dto.response.KnowledgeQueryDebugResponse;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
import com.jujiu.agent.model.entity.KbQueryLog;
import com.jujiu.agent.model.entity.KbRetrievalTrace;
import com.jujiu.agent.repository.KbQueryLogRepository;
import com.jujiu.agent.repository.KbRetrievalTraceRepository;
import com.jujiu.agent.service.kb.QueryLogService;
import com.jujiu.agent.service.kb.RagService;
import com.jujiu.agent.service.kb.RetrievalResultOrganizer;
import com.jujiu.agent.service.kb.VectorSearchService;
import com.jujiu.agent.service.kb.model.OrganizedRetrievalResult;
import com.jujiu.agent.service.kb.model.SearchDebugResult;
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
 * 统一承接知识库问答 API、Tool 调用和聊天增强能力。
 *
 * <p>主要职责包括：
 * <ul>
 *     <li>接收请求并兜底关键参数</li>
 *     <li>调用向量检索服务获取相关分块结果</li>
 *     <li>构造知识库上下文与引用列表</li>
 *     <li>调用 DeepSeekClient 生成答案（同步 / 流式）</li>
 *     <li>封装引用结果、记录查询日志与检索轨迹</li>
 *     <li>处理零命中与异常等边界场景</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {

    /** 默认检索模式：向量检索。 */
    private static final String DEFAULT_RETRIEVAL_MODE = "VECTOR";

    /** 默认查询来源：知识库 API。 */
    private static final String DEFAULT_QUERY_SOURCE = "KB_API";

    /** 默认查询状态：成功。 */
    private static final String DEFAULT_QUERY_STATUS_SUCCESS = "SUCCESS";

    /** 默认查询状态：未命中。 */
    private static final String DEFAULT_QUERY_STATUS_EMPTY = "EMPTY";

    /** 默认查询状态：失败。 */
    private static final String DEFAULT_QUERY_STATUS_FAILED = "FAILED";

    /** 流式输出缓冲最小长度（字节）。 */
    private static final int STREAM_FLUSH_MIN_LENGTH = 32;

    /** 向量检索服务。 */
    private final VectorSearchService vectorSearchService;

    /** DeepSeek 大模型客户端。 */
    private final DeepSeekClient deepSeekClient;

    /** 知识库查询日志仓储。 */
    private final KbQueryLogRepository kbQueryLogRepository;

    /** 知识库检索轨迹仓储。 */
    private final KbRetrievalTraceRepository kbRetrievalTraceRepository;

    /** 知识库配置属性。 */
    private final KnowledgeBaseProperties knowledgeBaseProperties;

    /** 流式问答异步执行器。 */
    private final ExecutorService chatExecutor;

    /** JSON 序列化器。 */
    public final ObjectMapper objectMapper;

    /** 查询日志服务。 */
    private final QueryLogService queryLogService;

    /** 检索结果整理器。 */
    private final RetrievalResultOrganizer retrievalResultOrganizer;
    
    /**
     * 构造 RAG 问答服务。
     *
     * @param vectorSearchService         向量检索服务
     * @param deepSeekClient              DeepSeek 大模型客户端
     * @param kbQueryLogRepository        知识库查询日志仓储
     * @param kbRetrievalTraceRepository  知识库检索轨迹仓储
     * @param knowledgeBaseProperties     知识库配置属性
     * @param objectMapper                JSON 序列化器
     * @param chatExecutor                流式问答异步执行器
     * @param queryLogService             查询日志服务
     */
    public RagServiceImpl(VectorSearchService vectorSearchService,
                          DeepSeekClient deepSeekClient,
                          KbQueryLogRepository kbQueryLogRepository,
                          KbRetrievalTraceRepository kbRetrievalTraceRepository,
                          KnowledgeBaseProperties knowledgeBaseProperties,
                          ObjectMapper objectMapper,
                          ExecutorService chatExecutor,
                          RetrievalResultOrganizer retrievalResultOrganizer,
                          QueryLogService queryLogService) {
        this.vectorSearchService = vectorSearchService;
        this.deepSeekClient = deepSeekClient;
        this.kbQueryLogRepository = kbQueryLogRepository;
        this.kbRetrievalTraceRepository = kbRetrievalTraceRepository;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.objectMapper = objectMapper;
        this.chatExecutor = chatExecutor;
        this.retrievalResultOrganizer = retrievalResultOrganizer;
        this.queryLogService = queryLogService;
    }

    /**
     * 执行同步知识库问答。
     *
     * <p>完整流程：参数校验 -> 向量检索 -> 引用/上下文组装 -> 模型生成 -> 日志记录 -> 结果封装。
     *
     * @param userId  当前用户 ID
     * @param request 知识库问答请求
     * @return 知识库问答响应结果/
     */
    @Override
    @Transactional
    public KnowledgeQueryResponse query(Long userId, QueryKnowledgeBaseRequest request) {
        // 1. 校验请求参数合法性
        validateRequest(userId, request);

        // 2. 初始化计时与兜底参数
        long startTime = System.currentTimeMillis();
        Long kbId = request.getKbId() == null ? 1L : request.getKbId();
        Integer topK = request.getTopK() == null ? 5 : request.getTopK();

        log.info("[KB][QUERY] 开始执行知识库问答 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, request.getQuestion().length()
        );

        // 3. 执行向量检索
        List<ChunkSearchResult> searchResults = vectorSearchService.search(
                kbId,
                userId,
                request.getQuestion(),
                topK);

        // 4. 对原始候选结果执行统一整理，统一生成最终上下文与引用。
        //    这样后续 query / stream / 工具 / 显式知识增强都可以复用同一套逻辑。
        OrganizedRetrievalResult organizedResult = retrievalResultOrganizer.organize(
                searchResults, 
                request.getQuestion()
        );
        
        // 5. 若整理后仍无可用结果，则按统一空结果语义返回。
        if (organizedResult.getFinalResults() == null || organizedResult.getFinalResults().isEmpty()) {
            logAclAwareEmptyResult(userId, kbId, "KB_API");
            log.info("[KB][QUERY][ORGANIZE] 检索整理后无可用结果 - kbId={}, userId={}, rawResultCount={}, emptyReason={}",
                    kbId,
                    userId,
                    organizedResult.getRawResultCount(),
                    organizedResult.getEmptyReason());

            return handleEmptyResult(userId, kbId, request, topK, startTime);
        }

        // 6. 从整理结果中统一读取 citations 和 context，避免各链路重复组装。
        List<CitationResponse> citations = organizedResult.getCitations();
        String context = organizedResult.getContext();
        List<DeepSeekMessage> messages = buildPromptMessages(request.getQuestion(), context);

        log.info("[KB][QUERY][ORGANIZE] 检索结果整理完成并准备调用模型 - kbId={}, userId={}, rawResultCount={}, finalResultCount={}, citationCount={}, contextLength={}",
                kbId,
                userId,
                organizedResult.getRawResultCount(),
                organizedResult.getFinalResultCount(),
                citations == null ? 0 : citations.size(),
                context == null ? 0 : context.length()
        );

        // 7. 调用 DeepSeek 生成答案
        DeepSeekResult deepSeekResult = deepSeekClient.chat(messages);

        // 8. 计算耗时
        long latencyMs = System.currentTimeMillis() - startTime;
        log.info("[KB][QUERY] 知识库问答完成 - kbId={}, userId={}, topK={}, questionLength={}, latencyMs={}",
                kbId, userId, topK, request.getQuestion().length(), latencyMs
        );

        // 9. 保存查询日志与检索轨迹
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
        queryLogService.saveRetrievalTrace(queryLog.getId(), organizedResult.getFinalResults());

        // 10. 封装并返回响应结果
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
     * @param userId   当前用户 ID
     * @param kbId     知识库 ID
     * @param question 用户问题
     * @param topK     检索数量
     * @return 知识库上下文文本，未命中时返回空字符串
     */
    @Override
    public String buildKnowledgeContext(Long userId, Long kbId, String question, Integer topK) {
        // 1. 校验关键参数
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }

        // 2. 兜底知识库 ID 与检索数量
        Long targetKbId = kbId == null ? 1L : kbId;
        Integer targetTopK = (topK == null || topK <= 0) ? 5 : topK;

        log.info("[KB][CONTEXT] 开始构造知识上下文 - kbId={}, userId={}, topK={}, questionLength={}",
                targetKbId, userId, targetTopK, question.length());

        // 3. 执行检索，获取原始结果。
        List<ChunkSearchResult> searchResults = vectorSearchService.search(
                targetKbId,
                userId,
                question,
                targetTopK);

        // 4. 统一整理检索结果，确保聊天显式知识增强与 kb/query 共用同一套上下文生成逻辑。
        OrganizedRetrievalResult organizedResult = retrievalResultOrganizer.organize(
                searchResults, 
                question);

        // 5. 若整理后无结果，则返回空字符串，保持当前聊天增强链路兼容。
        if (organizedResult.getFinalResults() == null || organizedResult.getFinalResults().isEmpty()) {
            logAclAwareEmptyResult(userId, kbId, "KB_API");
            log.info("[KB][CONTEXT][ORGANIZE] 知识上下文整理后为空 - kbId={}, userId={}, rawResultCount={}, emptyReason={}",
                    targetKbId,
                    userId,
                    organizedResult.getRawResultCount(),
                    organizedResult.getEmptyReason());
            return "";
        }
        
        // 6. 返回统一整理后的上下文。
        return organizedResult.getContext();
    }

    /**
     * 执行知识库问答调试。
     *
     * <p>该方法不会调用大模型，只返回：
     * <ul>
     *     <li>检索层中间态结果</li>
     *     <li>organizer 最终结果</li>
     *     <li>citation/context/emptyReason</li>
     * </ul>
     *
     * @param userId  当前用户 ID
     * @param request 请求参数
     * @return 调试响应
     */
    @Override
    public KnowledgeQueryDebugResponse debugQuery(Long userId, QueryKnowledgeBaseRequest request) {
        // 1. 复用现有参数校验逻辑。
        validateRequest(userId, request);

        Long kbId = request.getKbId() == null ? 1L : request.getKbId();
        Integer topK = request.getTopK() == null ? 5 : request.getTopK();

        log.info("[KB][QUERY][DEBUG] 开始执行知识库调试问答 - kbId={}, userId={}, topK={}, questionLength={}",
                kbId, userId, topK, request.getQuestion().length());

        // 2. 获取检索层中间态结果。
        SearchDebugResult searchDebugResult = vectorSearchService.debugSearch(
                kbId,
                userId,
                request.getQuestion(),
                topK
        );

        // 3. organizer 继续基于 balancedCandidates 生成最终证据结果。
        OrganizedRetrievalResult organizedResult = retrievalResultOrganizer.organize(
                searchDebugResult.getBalancedCandidates(),
                request.getQuestion()
        );

        log.info("[KB][QUERY][DEBUG] 知识库调试问答完成 - kbId={}, userId={}, finalResultCount={}, citationCount={}, emptyReason={}",
                kbId,
                userId,
                organizedResult.getFinalResultCount(),
                organizedResult.getCitations() == null ? 0 : organizedResult.getCitations().size(),
                organizedResult.getEmptyReason());

        // 4. 返回完整调试视图。
        return KnowledgeQueryDebugResponse.builder()
                .vectorCandidates(searchDebugResult.getVectorCandidates())
                .bm25Candidates(searchDebugResult.getBm25Candidates())
                .mergedCandidates(searchDebugResult.getMergedCandidates())
                .balancedCandidates(searchDebugResult.getBalancedCandidates())
                .finalResults(organizedResult.getFinalResults())
                .citations(organizedResult.getCitations())
                .context(organizedResult.getContext())
                .emptyReason(organizedResult.getEmptyReason())
                .build();
    }

    /**
     * 执行知识库流式问答。
     *
     * <p>当前实现先同步完成检索、引用与 Prompt 组装，
     * 再调用 DeepSeek 流式接口逐段返回模型生成内容。
     *
     * @param userId  当前用户 ID
     * @param request 知识库问答请求
     * @return SSE 发射器
     */
    @Override
    public SseEmitter queryStream(Long userId, QueryKnowledgeBaseRequest request) {
        // 1. 校验请求参数
        validateRequest(userId, request);

        // 2. 创建 SSE 发射器并提交异步任务
        SseEmitter emitter = new SseEmitter(0L);
        chatExecutor.submit(() -> {
            long startTime = System.currentTimeMillis();
            Long kbId = request.getKbId() == null ? 1L : request.getKbId();
            Integer topK = request.getTopK() == null ? 5 : request.getTopK();

            try {
                log.info("[KB][QUERY][STREAM] 开始执行知识库流式问答 - kbId={}, userId={}, topK={}, questionLength={}",
                        kbId, userId, topK, request.getQuestion().length());

                // 3. 执行检索，获取原始候选结果。
                List<ChunkSearchResult> searchResults = vectorSearchService.search(
                        kbId,
                        userId,
                        request.getQuestion(),
                        topK
                );

                // 4. 对检索结果执行统一整理，确保流式问答和同步问答使用同一套上下文/引用逻辑。
                OrganizedRetrievalResult organizedResult = retrievalResultOrganizer.organize(
                        searchResults,
                        request.getQuestion()
                );
                
                // 5. 若整理后没有可用结果，则返回统一空回答并结束流。
                if (organizedResult.getFinalResults() == null || organizedResult.getFinalResults().isEmpty()) {
                    logAclAwareEmptyResult(userId, kbId, "KB_API_STREAM");
                    
                    log.info("[KB][QUERY][STREAM][ORGANIZE] 检索整理后无可用结果 - kbId={}, userId={}, rawResultCount={}, emptyReason={}",
                            kbId,
                            userId,
                            organizedResult.getRawResultCount(),
                            organizedResult.getEmptyReason()
                    );

                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("抱歉，知识库中没有足够信息支持回答该问题。"));

                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("STREAM_FINISHED"));
                    emitter.complete();
                    return;
                }

                // 6. 从统一整理结果中获取 citations 与 context。
                List<CitationResponse> citations = organizedResult.getCitations();
                String context = organizedResult.getContext();
                List<DeepSeekMessage> messages = buildPromptMessages(request.getQuestion(), context);

                log.info("[KB][QUERY][STREAM][ORGANIZE] 检索结果整理完成并准备流式调用模型 - kbId={}, userId={}, rawResultCount={}, finalResultCount={}, citationCount={}, contextLength={}",
                        kbId,
                        userId,
                        organizedResult.getRawResultCount(),
                        organizedResult.getFinalResultCount(),
                        citations == null ? 0 : citations.size(),
                        context == null ? 0 : context.length()
                );

                // 7. 流式消费模型输出并按策略推送
                StringBuilder answerBuilder = new StringBuilder();
                StringBuilder chunkBuffer = new StringBuilder();
                int pushCount = 0;

                for (String content : deepSeekClient.chatStream(messages).toIterable()) {
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

                // 8. 推送缓冲区剩余内容
                if (!chunkBuffer.isEmpty()) {
                    String output = chunkBuffer.toString();
                    pushCount++;

                    log.info("[KB][QUERY][STREAM] 推送最后流式片段 - kbId={}, userId={}, pushCount={}, chunkLength={}, totalLength={}",
                            kbId, userId, pushCount, output.length(), answerBuilder.length());

                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(output));
                }

                // 9. 发送完成事件并关闭流
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("STREAM_FINISHED"));
                emitter.complete();

                long latencyMs = System.currentTimeMillis() - startTime;
                log.info("[KB][QUERY][STREAM] 知识库流式问答完成 - kbId={}, userId={}, answerLength={}, latencyMs={}",
                        kbId, userId, answerBuilder.length(), latencyMs);

            } catch (Exception e) {
                // 10. 异常处理：记录日志、推送错误事件、关闭流
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

        // 11. 返回 SSE 发射器
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

    /**
     * 获取知识库问答 Prompt 模板。
     *
     * <p>优先读取配置文件中的自定义模板，若未配置则返回默认模板。
     *
     * @return Prompt 模板字符串
     */
    private String getPromptTemplate() {
        // 1. 尝试从配置中读取自定义 Prompt 模板
        if (knowledgeBaseProperties != null 
                && knowledgeBaseProperties.getRag() != null 
                && knowledgeBaseProperties.getRag().getPromptTemplate() != null
                && !knowledgeBaseProperties.getRag().getPromptTemplate().isBlank()){
            return knowledgeBaseProperties.getRag().getPromptTemplate();
        }
        
        // 2. 返回默认模板
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
     * 校验知识库问答请求参数。
     *
     * @param userId  当前用户 ID
     * @param request 知识库问答请求
     * @throws BusinessException 参数校验失败时抛出业务异常
     */
    private void validateRequest(Long userId, QueryKnowledgeBaseRequest request) {
        // 1. 校验用户 ID
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }
        // 2. 校验请求对象不能为空
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "request 不能为空");
        }
        // 3. 校验问题内容不能为空
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "question 不能为空");
        }
    }

    /**
     * 记录 ACL 感知的空检索结果日志。
     *
     * @param userId 当前用户 ID
     * @param kbId   知识库 ID
     * @param source 查询来源标识
     */
    private void logAclAwareEmptyResult(Long userId, Long kbId, String source) {
        log.info("[KB][ACL] 检索结果为空 - source={}, kbId={}, userId={}, reason=NO_ACCESSIBLE_OR_MATCHED_DOCUMENT",
                source, kbId, userId);
    }
}
