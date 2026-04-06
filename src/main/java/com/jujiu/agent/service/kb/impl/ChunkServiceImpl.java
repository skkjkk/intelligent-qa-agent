package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.service.kb.ChunkService;
import com.jujiu.agent.model.entity.KbChunk;
import com.jujiu.agent.repository.KbChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 分快服务
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 14:06
 */
@Service
@Slf4j
public class ChunkServiceImpl implements ChunkService {
    private final KbChunkRepository kbChunkRepository;

    private final KnowledgeBaseProperties properties;

    public ChunkServiceImpl(KbChunkRepository kbChunkRepository, KnowledgeBaseProperties properties) {
        this.kbChunkRepository = kbChunkRepository;
        this.properties = properties;
    }

    @Override
    public List<KbChunk> split(Long documentId, String content) {
        if (content == null || content.isBlank()) {
            log.warn("content 为空或空白");
            return List.of();
        }
        Integer chunkSize = properties.getChunking().getDefaultSize();
        if (chunkSize == null || chunkSize <= 0) {
            chunkSize = 500;
        }

        Integer overlap = properties.getChunking().getDefaultOverlap();
        if (overlap == null || overlap <= 0) {
            overlap = 50;
        }

        String text = content.trim();
        int length = text.length();

        List<KbChunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunkContent = text.substring(start, end);

            KbChunk chunk = KbChunk.builder()
                    .documentId(documentId)
                    .chunkIndex(index++)
                    .content(chunkContent)
                    .charCount(chunkContent.length())
                    .tokenCount(estimateTokenCount(chunkContent))
                    .enabled(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            chunks.add(chunk);

            if (end == length) {
                break;
            }

            start = end - overlap;
        }

        return chunks;
    }

    @Override
    public void saveChunks(List<KbChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        for (KbChunk chunk : chunks) {
            kbChunkRepository.insert(chunk);
        }
    }

    @Transactional
    @Override
    public List<KbChunk> splitAndSave(Long documentId, String content) {
        List<KbChunk> chunks = split(documentId, content);
        saveChunks(chunks);
        return chunks;
    }
    
    /**
     * 估算 Token 数。
     *
     * <p>首版采用简化策略：中文按 1 字符 ≈ 0.6 token 估算，
     * 英文按空格分词估算。为了简化，可直接用 {@code chars / 2}。
     *
     * @param text 文本内容
     * @return 估算的 token 数
     */
    private int estimateTokenCount(String text) {
        // 简单估算：平均每 2 个字符约等于 1 个 token
        return Math.max(1, text.length() / 2);
    }
}
