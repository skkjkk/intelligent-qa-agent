package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.request.QueryKnowledgeBaseRequest;
import com.jujiu.agent.model.dto.response.KnowledgeQueryResponse;
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

}
