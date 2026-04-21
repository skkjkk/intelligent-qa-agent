package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.response.KbHealthResponse;

/**
 * 知识库健康检查服务接口。
 *
 * <p>统一负责知识库相关依赖的健康状态检测。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public interface KnowledgeBaseHealthService {
    /**
     * 执行知识库健康检查。
     *
     * @return 健康检查结果
     */
    KbHealthResponse checkHealth();
}
