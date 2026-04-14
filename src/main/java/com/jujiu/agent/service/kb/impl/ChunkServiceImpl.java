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
 * 文档内容分块服务实现类。
 * <p>
 * 负责将文档内容按配置的大小和重叠度切割为 {@link KbChunk} 列表，
 * 并提供分块数据的持久化能力。
 * </p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 14:06
 */
@Service
@Slf4j
public class ChunkServiceImpl implements ChunkService {

    /** 分块数据持久化仓库。 */
    private final KbChunkRepository kbChunkRepository;

    /** 知识库配置属性，用于获取分块大小、重叠度等参数。 */
    private final KnowledgeBaseProperties properties;

    /**
     * 构造方法。
     *
     * @param kbChunkRepository 分块数据持久化仓库
     * @param properties        知识库配置属性
     */
    public ChunkServiceImpl(KbChunkRepository kbChunkRepository, KnowledgeBaseProperties properties) {
        this.kbChunkRepository = kbChunkRepository;
        this.properties = properties;
    }

    /**
     * 将文档内容按配置规则切分为多个 {@link KbChunk}。
     *
     * @param documentId 文档 ID
     * @param content    原始文档内容
     * @return 切分后的分块列表；若内容为空则返回空列表
     */
    @Override
    public List<KbChunk> split(Long documentId, String content) {
        // 1. 校验内容是否为空或空白
        if (content == null || content.isBlank()) {
            log.warn("content 为空或空白");
            return List.of();
        }

        // 2. 读取并校验分块大小，若无效则使用默认值 500
        Integer chunkSize = properties.getChunking().getDefaultSize();
        if (chunkSize == null || chunkSize <= 0) {
            chunkSize = 500;
        }

        // 3. 读取并校验重叠度，若无效则使用默认值 50
        Integer overlap = properties.getChunking().getDefaultOverlap();
        if (overlap == null || overlap <= 0) {
            overlap = 50;
        }

        // 4. 去除首尾空白并获取文本长度
        String text = content.trim();
        int length = text.length();

        // 5. 初始化分块集合及滑动窗口指针
        List<KbChunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;

        // 6. 按滑动窗口循环切分文本
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunkContent = text.substring(start, end);

            // 7. 构建分块实体并填充基础字段
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

            // 8. 将分块加入结果集合
            chunks.add(chunk);

            // 9. 若已切分到末尾则退出循环
            if (end == length) {
                break;
            }

            // 10. 移动窗口起点，保留重叠部分
            start = end - overlap;
        }

        return chunks;
    }

    /**
     * 将分块列表持久化到数据库。
     *
     * @param chunks 待保存的分块列表
     */
    @Override
    public void saveChunks(List<KbChunk> chunks) {
        // 1. 校验列表是否为空
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        // 2. 遍历并逐个插入分块记录
        for (KbChunk chunk : chunks) {
            kbChunkRepository.insert(chunk);
        }
    }

    /**
     * 先切分文档内容，再持久化分块，整个流程在事务中执行。
     *
     * @param documentId 文档 ID
     * @param content    原始文档内容
     * @return 切分并保存后的分块列表
     */
    @Transactional
    @Override
    public List<KbChunk> splitAndSave(Long documentId, String content) {
        // 1. 执行文本切分
        List<KbChunk> chunks = split(documentId, content);
        // 2. 持久化分块数据
        saveChunks(chunks);
        // 3. 返回分块结果
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
