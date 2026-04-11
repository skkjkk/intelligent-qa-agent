package com.jujiu.agent.service.kb.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 向量化服务单元测试。
 *
 * <p>用于验证输入校验、缓存命中、缓存异常降级以及配置校验等核心逻辑。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
class EmbeddingServiceImplTest {

    private KnowledgeBaseProperties properties;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private WebClient.Builder webClientBuilder;
    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        properties = mock(KnowledgeBaseProperties.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        webClientBuilder = mock(WebClient.Builder.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(webClientBuilder.build()).thenReturn(mock(WebClient.class));

        KnowledgeBaseProperties.Embedding embedding = new KnowledgeBaseProperties.Embedding();
        embedding.setApiUrl("https://open.bigmodel.cn/api/paas/v4/embeddings");
        embedding.setApiKey("test-api-key");
        embedding.setModel("embedding-3");
        embedding.setDimension(2048);

        when(properties.getEmbedding()).thenReturn(embedding);

        embeddingService = new EmbeddingServiceImpl(
                properties,
                redisTemplate,
                new ObjectMapper(),
                webClientBuilder
        );
    }

    @Test
    @DisplayName("embedQuery 文本为空时应抛异常")
    void shouldThrowWhenQueryTextIsBlank() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> embeddingService.embedQuery(""));

        assertTrue(exception.getMessage().contains("待向量化文本不能为空"));
    }

    @Test
    @DisplayName("embedDocument 缓存命中时应直接返回缓存向量")
    void shouldReturnCachedVectorWhenCacheHit() {
        String cachedJson = "[0.1,0.2,0.3]";

        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        float[] result = embeddingService.embedDocument("缓存命中文本");

        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.0001f);
        assertEquals(0.2f, result[1], 0.0001f);
        assertEquals(0.3f, result[2], 0.0001f);

        verify(valueOperations, times(1)).get(anyString());
    }

    @Test
    @DisplayName("embedDocument 配置缺失时应抛异常")
    void shouldThrowWhenEmbeddingConfigMissing() {
        when(properties.getEmbedding()).thenReturn(null);

        EmbeddingServiceImpl service = new EmbeddingServiceImpl(
                properties,
                redisTemplate,
                new ObjectMapper(),
                webClientBuilder
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.embedDocument("测试文本"));

        assertTrue(exception.getMessage().contains("knowledge-base.embedding 配置缺失"));
    }

    @Test
    @DisplayName("embedDocument 缓存损坏时应尝试降级")
    void shouldFallbackWhenCacheDataBroken() {
        when(valueOperations.get(anyString())).thenReturn("not-json");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> embeddingService.embedDocument("缓存损坏文本"));

        assertNotNull(exception);
        verify(valueOperations, times(1)).get(anyString());
    }
}
