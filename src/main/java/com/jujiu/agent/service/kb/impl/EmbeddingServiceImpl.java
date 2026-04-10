package com.jujiu.agent.service.kb.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.service.kb.EmbeddingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final String CACHE_KEY_PREFIX = "kb:embedding";
    private static final long CACHE_TTL_HOURS = 24L;
    private static final int DEFAULT_DIMENSION = 2048;
    
    private final KnowledgeBaseProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
     public EmbeddingServiceImpl(KnowledgeBaseProperties properties,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 WebClient.Builder webClientBuilder ) {
         this.properties = properties;
         this.redisTemplate = redisTemplate;
         this.objectMapper = objectMapper;
         this.webClient = webClientBuilder.build();
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
        return doEmbed(text,"QUERY");
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
        return doEmbed(text,"DOCUMENT");
    }

    /**
     * 执行统一向量化逻辑。
     *
     * @param text 待向量化文本
     * @param scene 向量化场景，便于日志区分
     * @return 向量结果
     */
    private float[] doEmbed(String text, String scene) {
        String model = getEmbeddingModel();
        String cacheKey = buildCacheKey(model, text);

        log.info("[KB][EMBEDDING] 开始向量化 - scene={}, model={}, textLength={}", scene, model, text.length());
        
       float[] cacheVector = getVectorFromCache(cacheKey,scene);
        if (cacheVector != null) {
            return cacheVector;
         }

        long stratTime = System.currentTimeMillis();
        float[] remoteVector = requestEmbedding(text, scene, model);
        long latencyMs = System.currentTimeMillis() - stratTime;

        validateVector(remoteVector, model);

        log.info("[KB][EMBEDDING][REMOTE] 远端向量化成功 - scene={}, model={}, dimension={}, latencyMs={}",
                scene, model, remoteVector.length, latencyMs);

        saveVectorToCache(cacheKey, remoteVector, scene);
        return remoteVector;

    }
    
    /**
     * 将向量结果写入 Redis 缓存。
     *
     * @param cacheKey 缓存 Key
     * @param vector 向量结果
     * @param scene 向量化场景
     */
    private void saveVectorToCache(String cacheKey, float[] vector, String scene) {
        if (!enableCache()) {
            return;
        }
        
        try {
            String cacheValue = objectMapper.writeValueAsString(vector);
            redisTemplate.opsForValue().set(
                    cacheKey,
                    cacheValue,
                    CACHE_TTL_HOURS,
                    TimeUnit.HOURS
            );

            log.info("[KB][EMBEDDING][CACHE] 缓存写入成功 - scene={}, cacheKey={}, dimension={}",
                    scene, cacheKey, vector.length);
        } catch (JsonProcessingException e) {
            log.warn("[KB][EMBEDDING][CACHE] 缓存序列化失败 - scene={}, cacheKey={}", scene, cacheKey, e);
        } catch (Exception e) {
            log.warn("[KB][EMBEDDING][CACHE] 缓存写入失败 - scene={}, cacheKey={}", scene, cacheKey, e);
        }
    }

    /**
     * 校验向量结果。
     *
     * @param vector 向量结果
     * @param model 模型名称
     */
    private void validateVector(float[] vector, String model) {
        if (vector == null || vector.length == 0) {
            log.error("[KB][EMBEDDING][REMOTE] 远端向量化结果异常 - model={}, dimension={}", model, vector == null ? null : vector.length);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 返回空向量");
        }

        int expectedDimension = getEmbeddingDimension();
        if (vector.length != expectedDimension) {
            log.warn("[KB][EMBEDDING] 向量维度与配置不一致 - model={}, expectedDimension={}, actualDimension={}",
                    model, expectedDimension, vector.length);
         }
    }

    /**
     * 获取向量维度配置。
     *
     * @return 向量维度
     */
    private int getEmbeddingDimension() {
        Integer dimension = properties.getEmbedding().getDimension();
        return (dimension == null || dimension <= 0) ? DEFAULT_DIMENSION : dimension;
    }
    
    /**
     * 调用外部 Embedding 服务。
     *
     * @param text 待向量化文本
     * @param scene 向量化场景
     * @param model 模型名称
     * @return 向量结果
     */
    private float[] requestEmbedding(String text, String scene, String model) {
        
        validateEmbeddingConfig();

        String apiUrl = properties.getEmbedding().getApiUrl();

        EmbeddingRequest request = new EmbeddingRequest();
        request.setInput(List.of(text));
        request.setModel(model);
        
        log.info("[KB][EMBEDDING][REMOTE] 开始调用远端 Embedding 服务 - scene={}, model={}, apiUrl={}, textLength={}",
                scene, model, apiUrl, text.length());
        
        try {
            EmbeddingResponse response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + properties.getEmbedding().getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(errorBody -> {
                                        log.error("[KB][EMBEDDING][REMOTE] Embedding 接口返回错误状态 - scene={}, model={}, statusCode={}, responseBody={}",
                                                scene, model, clientResponse.statusCode().value(), errorBody);
                                        return new BusinessException(ResultCode.SYSTEM_ERROR,
                                                "Embedding 服务调用失败，HTTP状态码=" + clientResponse.statusCode().value());
                                    })
                    )
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            return parseEmbeddingResponse(response, scene, model, text.length());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[KB][EMBEDDING][REMOTE] 远端向量化失败 - scene={}, model={}, textLength={}",
                    scene, model, text.length(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 服务调用失败");
        }
    }

    /**
     * 解析 Embedding 接口响应。
     *
     * @param response 响应对象
     * @param scene 向量化场景
     * @param model 模型名称
     * @param textLength 原始文本长度
     * @return 向量结果
     */
    private float[] parseEmbeddingResponse(EmbeddingResponse response, 
                                           String scene, 
                                           String model, 
                                           int textLength) {
        if (response == null) {
            log.error("[KB][EMBEDDING][REMOTE] Embedding 响应为空 - scene={}, model={}, textLength={}",
                    scene, model, textLength);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 响应为空");
        }
        
        log.info("[KB][EMBEDDING][REMOTE] 收到 Embedding 响应 - scene={}, model={}, dataSize={}",
                scene, model, response.getData() == null ? 0 : response.getData().size());
        
        if (response.getData() == null || response.getData().isEmpty()) {
            log.error("[KB][EMBEDDING][REMOTE] Embedding 响应数据为空 - scene={}, model={}, textLength={}",
                    scene, model, textLength);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 响应数据为空");
        }

        EmbeddingData firstData = response.getData().get(0);
        if (firstData == null || firstData.getEmbedding() == null || firstData.getEmbedding().isEmpty()) {
            log.error("[KB][EMBEDDING][REMOTE] Embedding 向量为空 - scene={}, model={}, textLength={}",
                    scene, model, textLength);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 返回空向量");
        }

        float[] vector = convertToFloatArray(firstData.getEmbedding());

        log.info("[KB][EMBEDDING][REMOTE] Embedding 响应解析成功 - scene={}, model={}, dimension={}",
                scene, model, vector.length);

        return vector;
    }

    /**
     * 将 {@link List} 形式的向量结果转换为基础类型数组。
     *
     * @param vectorList 向量列表
     * @return float 数组
     */
    private float[] convertToFloatArray(List<Double> vectorList) {
        if (vectorList == null || vectorList.isEmpty()) {
            return new float[0];
        }

        float[] result = new float[vectorList.size()];
        for (int i = 0; i < vectorList.size(); i++) {
            Double value = vectorList.get(i);
            result[i] = value == null ? 0F : value.floatValue();
        }
        return result;
    }

    /**
     * 校验 Embedding 配置。
     */
    private void validateEmbeddingConfig() {
        if (properties.getEmbedding() == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "knowledge-base.embedding 配置缺失");
        }

        if (properties.getEmbedding().getApiUrl() == null || properties.getEmbedding().getApiUrl().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding API URL 未配置");
        }

        if (properties.getEmbedding().getApiKey() == null || properties.getEmbedding().getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding API Key 未配置");
        }

        if (properties.getEmbedding().getModel() == null || properties.getEmbedding().getModel().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Embedding 模型未配置");
        }
    }

    /**
     * 构建缓存 Key。
     *
     * @param model 模型名称
     * @param text 原始文本
     * @return Redis 缓存 Key
     */
    private String buildCacheKey(String model, String text) {
        return CACHE_KEY_PREFIX + ":" + model + ":" + DigestUtil.md5Hex(text);
    }

    /**
     * 获取向量维度配置。
     *
     * @return 向量维度
     */
    private String getEmbeddingModel() {
        String model = properties.getEmbedding().getModel();
        return (model == null || model.isBlank()) ? "embedding-3" : model;
    }

    /**
     * 从 Redis 中读取缓存向量。
     *
     * @param cacheKey 缓存 Key
     * @param scene 向量化场景
     * @return 命中时返回向量，未命中返回 {@code null}
     */
    private float[] getVectorFromCache(String cacheKey, String scene) {

        if (!enableCache()) {
            return null;
        }
        
        try{
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue == null || cachedValue.isBlank()) {
                log.info("[KB][EMBEDDING][CACHE] 缓存未命中 - scene={}, cacheKey={}", scene, cacheKey);
                return null;
            }

            List<Double> vectorList = objectMapper.readValue(cachedValue, new TypeReference<List<Double>>() {});
            float[] vector = convertToFloatArray(vectorList);
            
            log.info("[KB][EMBEDDING][CACHE] 缓存命中 - scene={}, cacheKey={}, dimension={}",
                    scene, cacheKey, vector.length);
            
            return vector;
        } catch (Exception e) {
            log.warn("[KB][EMBEDDING][CACHE] 缓存读取失败，降级走远端调用 - scene={}, cacheKey={}",
                    scene, cacheKey, e);
            return null;
        }
        
    }

    /**
     * 判断当前是否启用 Embedding 缓存。
     *
     * @return true 表示启用缓存，false 表示跳过缓存
     */
    private boolean enableCache() {
        return redisTemplate != null;
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

    /**
     * Embedding 请求对象。
     */
    @Data
    private static class EmbeddingRequest{
        /**
         * 模型名称。
         */
        private String model;

        /**
         * 输入文本列表。
         */
        private List<String> input;
    }

    /**
     * Embedding 响应对象。
     */
    @Data
    private static class EmbeddingResponse {

        /**
         * 响应数据列表。
         */
        private List<EmbeddingData> data;
    }
    
    /**
     * Embedding 数据对象。
     */
    @Data
    private static class EmbeddingData {
        /**
         * 向量结果。
         */
        private List<Double> embedding;

        /**
         * 当前结果对应输入索引。
         */
        private Integer index;
    }

}
