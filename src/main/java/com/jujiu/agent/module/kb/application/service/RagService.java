package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.module.kb.api.response.KnowledgeQueryDebugResponse;
import com.jujiu.agent.module.kb.api.response.KnowledgeQueryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 问答服务接口。
 *
 * <p>负责统一编排知识库问答流程，是独立知识库问答接口的核心入口。
 *
 * <p>该服务负责：
 * <ul>
 *     <li>接收知识库问答请求</li>
 *     <li>调用检索服务获取候选分块</li>
 *     <li>构造上下文和引用</li>
 *     <li>调用大模型生成答案</li>
 *     <li>返回统一响应对象</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
public interface RagService {
    /**
     * 执行知识库问答。
     *
     * @param userId 当前用户 ID
     * @param request 知识库问答请求
     * @return 知识库问答响应结果
     */
    KnowledgeQueryResponse query(Long userId, QueryKnowledgeBaseRequest request);

    /**
     * 执行知识库问答（流式）。
     *
     * @param userId 当前用户 ID
     * @param request 知识库问答请求
     * @return 知识库问答响应结果
     */
    SseEmitter queryStream(Long userId, QueryKnowledgeBaseRequest request);

    /**
     * 构造知识库增强上下文。
     *
     * @param userId   当前用户 ID
     * @param kbId     知识库 ID
     * @param question 用户问题
     * @param topK     检索数量
     * @return 知识库上下文文本
     */
    String buildKnowledgeContext(Long userId, Long kbId, String question, Integer topK);

    /**
     * 执行知识库问答调试。
     *
     * <p>该方法不调用大模型，只返回检索与 organizer 中间态结果。
     *
     * @param userId  当前用户 ID
     * @param request 请求参数
     * @return 调试结果
     */
    KnowledgeQueryDebugResponse debugQuery(Long userId, QueryKnowledgeBaseRequest request);
}
