package com.jujiu.agent.service.kb.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/20 9:33
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientEmbeddingProviderClient implements EmbeddingProviderClient {
    private final WebClient.Builder webClientBuilder;
    private final KnowledgeBaseProperties properties;
    /**
     * 将文本转换为向量表示
     * 
     * <p>通过远程嵌入服务API将文本转换为向量表示。
     * 该方法实现了远程HTTP调用，包含配置验证、重试机制和错误处理。
     * 
     * <p>执行流程：
     * <ol>
     *     <li>验证嵌入服务配置完整性</li>
     *     <li>构建WebClient用于HTTP通信</li>
     *     <li>根据重试配置执行请求（支持指数退避）</li>
     *     <li>调用远程嵌入API获取向量</li>
     *     <li>解析响应并返回向量数组</li>
     * </ol>
     * 
     * <p>重试策略：
     * <ul>
     *     <li>429（速率限制）: 自动重试直到成功或重试次数耗尽</li>
     *     <li>5xx（服务器错误）: 自动重试直到成功或重试次数耗尽</li>
     *     <li>超时: 自动重试直到成功或重试次数耗尽</li>
     *     <li>4xx（客户端错误，除429）: 立即抛出异常，不重试</li>
     * </ul>
     * 
     * <p>退避算法：指数退避，每次重试等待时间为 min(maxBackoffMs, backoffMs * 2^(attempt-1))
     * 
     * @param text 需要嵌入的文本内容，不能为null或空字符串
     * @param scene 嵌入场景（QUERY或DOCUMENT），用于日志记录和后续扩展
     * @param model 嵌入模型标识，对应远程API的model参数
     * @return 文本的向量表示，float数组长度由嵌入模型决定
     * @throws BusinessException 配置验证失败时抛出
     * @throws BusinessException 所有重试次数耗尽后仍失败时抛出
     */
    @Override
    public float[] embed(String text, EmbeddingScene scene, String model) {
        // 1. 验证嵌入服务配置完整性
        validateConfig();
        
        // 2. 构建WebClient用于HTTP通信
        WebClient client = webClientBuilder.build();
        
        // 3. 配置重试参数
        int maxAttempts = properties.getEmbedding().getRetry().getMaxAttempts();
        long backoffMs = properties.getEmbedding().getRetry().getBackoffMs();
        long maxBackoffMs = properties.getEmbedding().getRetry().getMaxBackoffMs();
        boolean retryEnabled = Boolean.TRUE.equals(properties.getEmbedding().getRetry().getEnabled());
        
        // 4. 计算总重试次数
        int totalAttempts = retryEnabled ? Math.max(1, maxAttempts) : 1;
        
        // 5. 执行请求（支持指数退避）
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                long start = System.currentTimeMillis();
                // 6. 调用远程嵌入API获取向量
                EmbeddingResponse response = client.post()
                        .uri(properties.getEmbedding().getApiUrl())
                        .header("Authorization", "Bearer " + properties.getEmbedding().getApiKey())
                        .header("Content-Type", "application/json")
                        .bodyValue(new EmbeddingRequest(model, List.of(text)))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError,r->r.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> new RemoteStatusException(r.statusCode().value(),body)))
                        .bodyToMono(EmbeddingResponse.class)
                        .timeout(Duration.ofMillis(properties.getEmbedding().getTimeoutMs()))
                        .block();
                
                // 7. 解析响应并返回向量数组
                float[] vector = parseResponse(response);
                long cost = System.currentTimeMillis() - start;
                log.info("[KB][EMBEDDING][REMOTE] result=ok scene={} model={} attempt={} latencyMs={} dimension={}", 
                        scene, model, attempt, cost, vector.length);
                
                // 8. 返回向量数组
                return vector;
            }catch (RemoteStatusException ex){
                // 9. 处理重试逻辑
                if (ex.status == 429){
                    retryOrThrow(scene, model, attempt, totalAttempts, ex, ResultCode.EMBEDDING_REMOTE_RATE_LIMITED, backoffMs, maxBackoffMs);
                    continue;
                } if (ex.status >= 500) {
                    retryOrThrow(scene, model, attempt, totalAttempts, ex, ResultCode.EMBEDDING_REMOTE_ERROR, backoffMs, maxBackoffMs);
                    continue;
                }
                throw new BusinessException(ResultCode.EMBEDDING_REMOTE_ERROR, "Embedding HTTP " + ex.status);
            }catch (BusinessException ex){
                // 10. 处理重试逻辑
                if (isTimeout(ex)) {
                    retryOrThrow(scene, model, attempt, totalAttempts, ex, ResultCode.EMBEDDING_REMOTE_TIMEOUT, backoffMs, maxBackoffMs);
                    continue;
                }
                retryOrThrow(scene, model, attempt, totalAttempts, ex, ResultCode.EMBEDDING_REMOTE_ERROR, backoffMs, maxBackoffMs);
            }
        }
        throw new BusinessException(ResultCode.EMBEDDING_REMOTE_ERROR, "Embedding 调用失败");
    }
    
    /**
     * 执行重试或抛出异常
     * 
     * <p>处理嵌入服务调用失败的重试逻辑。
     * 根据当前重试次数决定是继续重试还是抛出异常。
     * 
     * <p>执行逻辑：
     * <ol>
     *     <li>记录重试日志</li>
     *     <li>判断重试次数是否已耗尽</li>
     *     <li>计算指数退避等待时间</li>
     *     <li>线程休眠等待后继续重试</li>
     * </ol>
     * 
     * <p>退避算法：指数退避
     * <ul>
     *     <li>第1次重试: sleep = min(maxBackoffMs, backoffMs * 2^0) = backoffMs</li>
     *     <li>第2次重试: sleep = min(maxBackoffMs, backoffMs * 2^1) = backoffMs * 2</li>
     *     <li>第3次重试: sleep = min(maxBackoffMs, backoffMs * 2^2) = backoffMs * 4</li>
     *     <li>以此类推...</li>
     * </ul>
     * 
     * @param scene 嵌入场景，用于日志记录
     * @param model 嵌入模型，用于日志记录
     * @param attempt 当前重试次数（从1开始）
     * @param totalAttempts 最大重试次数
     * @param ex 触发的异常
     * @param code 业务错误码
     * @param backoffMs 基础退避时间（毫秒）
     * @param maxBackoffMs 最大退避时间（毫秒）
     * @throws BusinessException 当重试次数已耗尽时抛出
     * @throws BusinessException 当线程被中断时抛出
     */
    private void retryOrThrow(EmbeddingScene scene,
                              String model,
                              int attempt,
                              int totalAttempts,
                              Exception ex,
                              ResultCode code,
                              long backoffMs,
                              long maxBackoffMs){
        // 1. 记录重试日志
        log.warn("[KB][EMBEDDING][REMOTE] result=retry scene={} model={} attempt={}/{} code={}",
                scene, model, attempt, totalAttempts, code.name(), ex);
        
        // 2. 判断重试次数是否已耗尽
        if (attempt >= totalAttempts) {
            throw new BusinessException(code, "Embedding 调用失败，重试耗尽");
        }
        
        // 3. 计算指数退避等待时间
        long sleep = Math.min(maxBackoffMs, backoffMs * (1L << Math.max(0, attempt - 1)));
        
        // 4. 线程休眠等待后继续重试
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(code, "Embedding 重试被中断");
        }
    }

    /**
     * 解析远程嵌入服务响应
     * 
     * <p>将远程API返回的EmbeddingResponse对象解析为float向量数组。
     * 该方法负责提取向量数据并进行类型转换。
     * 
     * <p>响应格式预期：
     * <pre>
     * {
     *     "data": [
     *         {
     *             "embedding": [0.1, 0.2, 0.3, ...],
     *             "index": 0
     *         }
     *     ]
     * }
     * </pre>
     * 
     * <p>处理流程：
     * <ol>
     *     <li>验证响应对象和data数组不为空</li>
     *     <li>获取第一个EmbeddingData元素</li>
     *     <li>验证embedding向量不为空</li>
     *     <li>将List&lt;Double&gt;转换为float[]</li>
     * </ol>
     * 
     * @param response 远程嵌入服务返回的响应对象
     * @return 文本的向量表示，float数组
     * @throws BusinessException 响应为空或格式异常时抛出
     * @throws BusinessException 响应包含null值时抛出
     */
    private float[] parseResponse(EmbeddingResponse response) {
        // 1. 验证响应对象和data数组不为空
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new BusinessException(ResultCode.EMBEDDING_RESPONSE_EMPTY, "Embedding 响应为空");
        }
        
        // 2. 获取第一个EmbeddingData元素
        EmbeddingData first = response.getData().get(0);

        // 3. 验证embedding向量不为空
        if (first == null || first.getEmbedding() == null || first.getEmbedding().isEmpty()) {
            throw new BusinessException(ResultCode.EMBEDDING_RESPONSE_FORMAT_ERROR, "Embedding 响应格式异常");
        }
        
        // 4. 创建与向量长度相同的float数组
        float[] vector = new float[first.getEmbedding().size()];
        for (int i = 0; i < first.getEmbedding().size(); i++) {
            Double v = first.getEmbedding().get(i);
            // 6. 验证每个元素都不为null
            if (v == null) {
                throw new BusinessException(ResultCode.EMBEDDING_RESPONSE_FORMAT_ERROR, "Embedding 响应包含 null");
            }
            
            // 7. 强制类型转换：Double -> float
            vector[i] = v.floatValue();
        }
        return vector;
    }

    /**
     * 验证嵌入服务配置完整性
     * 
     * <p>在调用远程嵌入服务之前，验证所有必要的配置项是否已正确配置。
     * 如果任何必需配置缺失或非法，将抛出业务异常阻止后续调用。
     * 
     * <p>验证项目：
     * <ol>
     *     <li>embedding配置对象是否存在</li>
     *     <li>API地址、API密钥、模型名称是否已配置</li>
     *     <li>向量维度是否为正整数</li>
     *     <li>超时时间是否为正整数</li>
     * </ol>
     * 
     * @throws BusinessException 当任何配置项缺失或非法时抛出
     */
    private void validateConfig() {
        // 1. 验证embedding配置对象是否存在
        if (properties.getEmbedding() == null) {
            throw new BusinessException(ResultCode.EMBEDDING_CONFIG_MISSING, "knowledge-base.embedding 配置缺失");
        }

        // 2. 验证核心配置（API地址、API密钥、模型名称）
        if (isBlank(properties.getEmbedding().getApiUrl())
                || isBlank(properties.getEmbedding().getApiKey())
                || isBlank(properties.getEmbedding().getModel())) {
            throw new BusinessException(ResultCode.EMBEDDING_CONFIG_INVALID, "Embedding 核心配置缺失");
        }

        // 3. 验证向量维度必须为正整数
        if (properties.getEmbedding().getDimension() == null || properties.getEmbedding().getDimension() <= 0) {
            throw new BusinessException(ResultCode.EMBEDDING_CONFIG_INVALID, "Embedding dimension 非法");
        }

        // 4. 验证超时时间必须为正整数
        if (properties.getEmbedding().getTimeoutMs() == null || properties.getEmbedding().getTimeoutMs() <= 0) {
            throw new BusinessException(ResultCode.EMBEDDING_CONFIG_INVALID, "Embedding timeoutMs 非法");
        }
    }

    /**
     * 判断异常是否由超时引起
     * 
     * <p>通过检查异常类型和异常链来判断当前异常是否表示超时情况。
     * 用于决定是否需要进行重试操作。
     * 
     * <p>判断逻辑：
     * <ol>
     *     <li>遍历异常链，检查异常类名是否包含"Timeout"字符串</li>
     *     <li>如果异常链中没有Timeout异常，检查是否是HTTP 408状态码</li>
     * </ol>
     * 
     * <p>支持的超时场景：
     * <ul>
     *     <li>WebClient超时（ReadTimeoutError、ConnectTimeoutError等）</li>
     *     <li>任何包含"Timeout"关键字的异常</li>
     *     <li>HTTP 408 Request Timeout 响应</li>
     * </ul>
     * 
     * @param ex 需要检查的异常对象
     * @return 如果是超时引起的异常返回true，否则返回false
     */
    private boolean isTimeout(Throwable ex) {
        // 1. 从当前异常开始遍历
        Throwable t = ex;

        // 2. 遍历异常链（向上追溯根本原因）
        while (t != null) {
            // 3. 检查当前异常的类名是否包含"Timeout"字符串
            if (t.getClass().getSimpleName().contains("Timeout")) {
                return true;// 找到超时异常，返回true
            }
            // 4. 继续向上查找Cause
            t = t.getCause();
        }
        // 5. 异常链中没有超时异常，检查是否是HTTP 408
        return ex instanceof WebClientResponseException w 
                && w.getStatusCode().value() == 408;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
    
    @Data
    private static class EmbeddingRequest {
        private final String model;
        private final List<String> input;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingResponse {
        private List<EmbeddingData> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingData {
        private List<Double> embedding;
        private Integer index;
    }

    private static class RemoteStatusException extends RuntimeException {
        private final int status;
        private final String body;

        private RemoteStatusException(int status, String body) {
            super("remote status=" + status + ", body=" + body);
            this.status = status;
            this.body = body;
        }
    }
}