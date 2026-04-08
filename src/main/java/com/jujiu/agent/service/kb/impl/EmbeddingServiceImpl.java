package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.service.kb.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 向量化服务实现。
 *
 * <p>当前阶段作为最小 RAG 闭环的 Embedding 统一入口，
 * 后续可在该类中接入真实的 Embedding API 调用与 Redis 缓存。
 *
 * <p>首版职责包括：
 * <ul>
 *     <li>参数校验</li>
 *     <li>查询文本向量化</li>
 *     <li>文档分块文本向量化</li>
 *     <li>后续扩展 Redis 缓存能力</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Service
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {
    
    private final KnowledgeBaseProperties properties;
    private final StringRedisTemplate redisTemplate;
    
     public EmbeddingServiceImpl(KnowledgeBaseProperties properties,
                                 StringRedisTemplate redisTemplate) {
        this.properties = properties;
         this.redisTemplate = redisTemplate;
    }
    
    
    /**
     * 对查询文本进行向量化。
     *
     * @param text 查询文本
     * @return 向量结果
     */
    @Override
    public float[] embedQuery(String text) {
        validateText(text);
        return doEmbed(text);
    }

    /**
     * 对文档分块文本进行向量化。
     *
     * @param text 文档分块文本
     * @return 向量结果
     */
    @Override
    public float[] embedDocument(String text) {
        validateText(text);
        return doEmbed(text);
    }
    
    /**
     * 执行统一向量化逻辑。
     *
     * <p>当前为骨架实现，后续在这里接入真实 Embedding API。
     *
     * @param text 待向量化文本
     * @return 向量结果
     */
    private float[] doEmbed(String text) {
        log.info("[KB][EMBEDDING] 统一向量化 - length={}", text.length());
        
        Integer dimension = properties.getEmbedding().getDimension();
        if (dimension == null && dimension <= 0) {
            dimension = 2048;
        }

        throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 服务尚未实现");
    }
    
    /**
     * 校验输入文本。
     *
     * @param text 输入文本
     */
    private void validateText(String text) {
        if (text == null || text.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "待向量化文本不能为空");
        }
    }
}
