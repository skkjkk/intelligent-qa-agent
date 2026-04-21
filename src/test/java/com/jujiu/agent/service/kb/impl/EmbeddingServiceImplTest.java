package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.module.kb.application.service.impl.EmbeddingServiceImpl;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.infrastructure.embedding.EmbeddingProviderClient;
import com.jujiu.agent.module.kb.infrastructure.embedding.EmbeddingScene;
import com.jujiu.agent.module.kb.infrastructure.embedding.RedisEmbeddingCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmbeddingServiceImplTest {

    private KnowledgeBaseProperties properties;
    private EmbeddingProviderClient providerClient;
    private RedisEmbeddingCache embeddingCache;
    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        properties = mock(KnowledgeBaseProperties.class);
        providerClient = mock(EmbeddingProviderClient.class);
        embeddingCache = mock(RedisEmbeddingCache.class);

        KnowledgeBaseProperties.Embedding embedding = new KnowledgeBaseProperties.Embedding();
        embedding.setModel("embedding-3");
        embedding.setDimension(3);

        when(properties.getEmbedding()).thenReturn(embedding);

        embeddingService = new EmbeddingServiceImpl(properties, providerClient, embeddingCache);
    }

    @Test
    @DisplayName("缓存命中时不调用 provider")
    void shouldReturnCacheWhenHit() {
        float[] cached = new float[]{0.11f, 0.22f, 0.33f};
        when(embeddingCache.get(eq("embedding-3"), eq(EmbeddingScene.DOCUMENT), eq("缓存命中文本")))
                .thenReturn(Optional.of(cached));

        float[] result = embeddingService.embedDocument("缓存命中文本");

        assertArrayEquals(cached, result);
        verify(providerClient, never()).embed(anyString(), any(), anyString());
        verify(embeddingCache, never()).put(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("缓存未命中时调用 provider 并回写缓存")
    void shouldCallProviderAndPutCacheWhenMiss() {
        when(embeddingCache.get(eq("embedding-3"), eq(EmbeddingScene.QUERY), eq("查询文本")))
                .thenReturn(Optional.empty());
        float[] remote = new float[]{0.1f, 0.2f, 0.3f};
        when(providerClient.embed(eq("查询文本"), eq(EmbeddingScene.QUERY), eq("embedding-3")))
                .thenReturn(remote);

        float[] result = embeddingService.embedQuery("查询文本");

        assertArrayEquals(remote, result);
        verify(providerClient, times(1)).embed("查询文本", EmbeddingScene.QUERY, "embedding-3");
        verify(embeddingCache, times(1)).put("embedding-3", EmbeddingScene.QUERY, "查询文本", remote);
    }

    @Test
    @DisplayName("provider 返回空向量时抛 EMBEDDING_RESPONSE_EMPTY")
    void shouldThrowWhenProviderReturnsEmptyVector() {
        when(embeddingCache.get(eq("embedding-3"), eq(EmbeddingScene.DOCUMENT), eq("空向量文本")))
                .thenReturn(Optional.empty());
        when(providerClient.embed(eq("空向量文本"), eq(EmbeddingScene.DOCUMENT), eq("embedding-3")))
                .thenReturn(new float[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> embeddingService.embedDocument("空向量文本"));

        assertEquals(ResultCode.EMBEDDING_RESPONSE_EMPTY, ex.getResultCode());
        verify(embeddingCache, never()).put(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("provider 返回维度不匹配时抛 EMBEDDING_DIMENSION_MISMATCH")
    void shouldThrowWhenProviderReturnsDimensionMismatch() {
        when(embeddingCache.get(eq("embedding-3"), eq(EmbeddingScene.DOCUMENT), eq("维度不匹配文本")))
                .thenReturn(Optional.empty());
        when(providerClient.embed(eq("维度不匹配文本"), eq(EmbeddingScene.DOCUMENT), eq("embedding-3")))
                .thenReturn(new float[]{1.0f, 2.0f});

        BusinessException ex = assertThrows(BusinessException.class,
                () -> embeddingService.embedDocument("维度不匹配文本"));

        assertEquals(ResultCode.EMBEDDING_DIMENSION_MISMATCH, ex.getResultCode());
        verify(embeddingCache, never()).put(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("输入为空白时抛 INVALID_PARAMETER")
    void shouldThrowWhenTextBlank() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> embeddingService.embedQuery("  "));

        assertEquals(ResultCode.INVALID_PARAMETER, ex.getResultCode());
        verifyNoInteractions(providerClient);
        verifyNoInteractions(embeddingCache);
    }
}
