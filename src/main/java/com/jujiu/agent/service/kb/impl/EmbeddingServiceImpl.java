package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.service.kb.EmbeddingService;
import com.jujiu.agent.service.kb.embedding.EmbeddingProviderClient;
import com.jujiu.agent.service.kb.embedding.EmbeddingScene;
import com.jujiu.agent.service.kb.embedding.RedisEmbeddingCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;



/**
 * 向量化服务实现。
 *
 * <p>统一负责知识库查询文本与文档分块文本的向量化处理，
 * 并对重复文本结果进行 Redis 缓存，减少重复调用外部 Embedding 服务。
 *
 * <p>当前实现职责包括：
 * <ul>
 *     <li>输入文本校验</li>
 *     <li>构造缓存 Key</li>
 *     <li>优先读取 Redis 缓存</li>
 *     <li>缓存未命中时调用外部 Embedding API</li>
 *     <li>解析向量结果并写回缓存</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/9
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {
    /** 知识库配置属性。 */
    private final KnowledgeBaseProperties properties;
    /** Embedding 服务客户端。 */
    private final EmbeddingProviderClient providerClient;
    /** Redis 缓存。 */
    private final RedisEmbeddingCache embeddingCache;
    
    
    /**
     * 对查询文本进行向量化。
     *
     * @param text 查询文本
     * @return 向量结果
     */
    @Override
    public float[] embedQuery(String text) {
        return doEmbed(text, EmbeddingScene.QUERY);
    }

    /**
     * 对文档分块文本进行向量化。
     *
     * @param text 文档分块文本
     * @return 向量结果
     */
    @Override
    public float[] embedDocument(String text) {
        return doEmbed(text, EmbeddingScene.DOCUMENT);
    }

    /**
     * 执行统一向量化逻辑。
     *
     * @param text 待向量化文本
     * @param scene 向量化场景，便于日志区分
     * @return 向量结果
     */
    private float[] doEmbed(String text, EmbeddingScene scene) {
        validateText(text);

        // 1. 获取当前使用的 Embedding 模型名称
        String model = properties.getEmbedding().getModel();
        
       // 2. 获取模型的向量维度
        Integer expectedDimension = properties.getEmbedding().getDimension();
        
        return embeddingCache.get(model, scene, text).orElseGet(()->{
            float[] vector = providerClient.embed(text, scene, model);
            validateVector(vector,expectedDimension,model,scene);
            embeddingCache.put(model, scene, text, vector);
            return vector;
        });
        
    }
    
    /**
     * 校验向量结果。
     *
     * @param vector 向量结果
     * @param model 模型名称
     */
    private void validateVector(float[] vector, 
                                Integer expectedDimension, 
                                String model,
                                EmbeddingScene scene) {
        if (vector == null || vector.length == 0) {
            throw new BusinessException(ResultCode.EMBEDDING_RESPONSE_EMPTY, "Embedding 返回空向量");
        }
        if (expectedDimension != null && expectedDimension > 0 && vector.length != expectedDimension) {
            log.error("[KB][EMBEDDING] result=dimension_mismatch scene={} model={} expected={} actual={}",
                    scene, model, expectedDimension, vector.length);
            throw new BusinessException(ResultCode.EMBEDDING_DIMENSION_MISMATCH,
                    "Embedding 维度不匹配，expected=" + expectedDimension + ", actual=" + vector.length);
        }
    }
    
    /**
     * 校验输入文本。
     *
     * @param text 输入文本
     */
    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "待向量化文本不能为空");
        }
    }
    
}
