package com.jujiu.agent.module.kb.infrastructure.embedding;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/20 9:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEmbeddingCache {
    private final KnowledgeBaseProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final LongAdder hit = new LongAdder();
    private final LongAdder miss = new LongAdder();
    private final LongAdder readError = new LongAdder();
    private final LongAdder writeError = new LongAdder();
    
    public Optional<float[]> get(String model, EmbeddingScene scene, String text) {
        if (!isEnabled()){
            return Optional.empty();
        }
        
        String key = buildKey(model, scene, text);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isEmpty()) {
                miss.increment();
                logRate("miss", scene, model);
                return Optional.empty();
            }
            
            float[] embedding = objectMapper.readValue(raw, float[].class);
            hit.increment();
            logRate("hit", scene, model);
            return Optional.of(embedding);
        } catch (Exception ex) {
            readError.increment();
            log.warn("[KB][EMBEDDING][CACHE] result=read_error scene={} model={} key={}", scene, model, key, ex);
            return Optional.empty();
        }
    }
    
    public void put(String model, EmbeddingScene scene, String text, float[] vector) {
        if (!isEnabled()){
            return;
        }
        
        String key = buildKey(model, scene, text);
        try {
            String val = objectMapper.writeValueAsString(vector);
            long ttl = properties.getEmbedding().getCache().getTtlHours();
            redisTemplate.opsForValue().set(key, val, ttl, TimeUnit.HOURS);
            log.info("[KB][EMBEDDING][CACHE] result=write_ok scene={} model={} key={} dimension={}", scene, model, key, vector.length);
        } catch (Exception ex) {
            writeError.increment();
            log.warn("[KB][EMBEDDING][CACHE] result=write_error scene={} model={} key={}", scene, model, key, ex);
        }
    }
    
    private boolean isEnabled() {
        return properties.getEmbedding() != null 
                && properties.getEmbedding().getCache() != null
                && Boolean.TRUE.equals(properties.getEmbedding().getCache().getEnabled())
                && redisTemplate != null;
    }
    
    private String buildKey(String model, EmbeddingScene scene, String text) {
        String prefix = properties.getEmbedding().getCache().getKeyPrefix();
        return prefix + ":" + model + ":" + scene.name() + ":" + DigestUtil.md5Hex(text);
    }

    private void logRate(String result, EmbeddingScene scene, String model) {
        long h = hit.sum();
        long m = miss.sum();
        long total = h + m;
        double hitRate = total == 0 ? 0D : (double) h / total;
        log.info("[KB][EMBEDDING][CACHE] result={} scene={} model={} hit={} miss={} hitRate={}", result, scene, model, h, m, String.format("%.4f", hitRate));
    }
}
