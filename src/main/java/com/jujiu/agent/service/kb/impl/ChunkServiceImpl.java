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
 *
 * <p>该类负责将解析后的纯文本切分为适合知识库检索、引用展示、上下文注入的 chunk 列表。
 *
 * <p>当前实现为分块层最终版主结构，采用以下固定策略：
 * <ol>
 *     <li>先对原始文本做标准化处理</li>
 *     <li>优先按段落边界切分</li>
 *     <li>将多个短段落拼装为适中大小的 chunk</li>
 *     <li>当单个段落过长时，优先按句子边界切分</li>
 *     <li>当单句仍然过长时，再按字符数执行最终兜底切分</li>
 *     <li>仅在最终兜底切分场景保留有限 overlap</li>
 * </ol>
 *
 * <p>该结构是分块层的最终版方向，后续只允许微调：
 * <ul>
 *     <li>chunkSize</li>
 *     <li>overlap</li>
 *     <li>句边界规则</li>
 * </ul>
 * 不再改动主流程层次。
 *
 * <p>这样做的目标是让 chunk 更接近完整语义单元，减少：
 * <ul>
 *     <li>相邻 chunk 高度重复</li>
 *     <li>citation 片段断裂</li>
 *     <li>snippet 截取证据不完整</li>
 *     <li>单文档大量相似 chunk 同时召回</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/17
 */
@Service
@Slf4j
public class ChunkServiceImpl implements ChunkService {
    /** 默认分块大小。 */
    private static final int DEFAULT_CHUNK_SIZE = 700;
    
    /** 默认重叠度。 */
    private static final int DEFAULT_OVERLAP = 80;

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
     * 将文档内容切分为多个 chunk。
     *
     * <p>最终版切分流程：
     * <ol>
     *     <li>校验输入</li>
     *     <li>读取分块参数</li>
     *     <li>标准化文本</li>
     *     <li>按段落切分</li>
     *     <li>优先按段落拼装 chunk</li>
     *     <li>超长段落改走句子优先切分</li>
     *     <li>超长句子再走字符数兜底</li>
     *     <li>生成稳定有序的 KbChunk 列表</li>
     * </ol>
     *
     * @param documentId 文档 ID
     * @param content    原始文档内容
     * @return 切分后的 chunk 列表
     */
    @Override
    public List<KbChunk> split(Long documentId, String content) {
        // 1. 输入为空时直接返回空列表，避免后续切分逻辑无意义执行。
        if (content == null || content.isBlank()) {
            log.warn("[KB][CHUNK] 文档内容为空或空白，跳过分块 - documentId={}", documentId);
            return List.of();
        }

        // 2. 读取并兜底分块配置。
        int chunkSize = getChunkSize();
        int overlap = getChunkOverlap();
        
        // 3. 对文本做标准化，保留段落边界但尽量清理噪声空白。
        String normalizedContent = normalizeContent(content);
        if (normalizedContent.isBlank()) {
            log.warn("[KB][CHUNK] 文本标准化后为空或空白，跳过分块 - documentId={}", documentId);
            return List.of();
        }

        log.info("[KB][CHUNK] 开始执行文档分块 - documentId={}, originalLength={}, normalizedLength={}, chunkSize={}, overlap={}",
                documentId,
                content.length(),
                normalizedContent.length(),
                chunkSize,
                overlap);

        // 4. 先按段落切分文本，作为后续 chunk 拼装的基础单元。
        List<String> paragraphs = splitToParagraphs(normalizedContent);
        
        log.info("[KB][CHUNK] 段落切分完成 - documentId={}, paragraphCount={}",
                documentId, paragraphs.size());

        // 5. 基于“段落优先、句子优先、字符兜底”的最终版策略组装 chunk 文本。
        List<String> chunkContents = assembleChunks(paragraphs, chunkSize, overlap);

        log.info("[KB][CHUNK] chunk 文本拼装完成 - documentId={}, chunkCount={}",
                documentId, chunkContents.size());

        // 6. 将 chunk 文本列表转换为 KbChunk 实体列表。
        List<KbChunk> chunks = new ArrayList<>();
        for (int i = 0; i< chunkContents.size(); i++) {
            String chunkContent = chunkContents.get(i);
            
            // 防御性过滤空chunk， 避免后续写入无意义记录
            if (chunkContent == null || chunkContent.isBlank()) {
                log.debug("[KB][CHUNK] 检测到空 chunk，已跳过 - documentId={}, chunkIndex={}", documentId, i);
                continue;
            }
            
            chunks.add(buildChunk(documentId, i, chunkContent));
        }
        
        log.info("[KB][CHUNK] chunk 构建完成 - documentId={}, chunkCount={}",
                documentId, chunks.size());

        return chunks;
    }

    /**
     * 对文档内容执行“切分 + 保存”。
     *
     * @param documentId 文档 ID
     * @param content    原始内容
     * @return 切分并保存后的 chunk 列表
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
     * 获取 chunkSize 配置。
     *
     * @return 分块大小
     */
    private int getChunkSize() {
        Integer size = properties.getChunking().getDefaultSize();
        if (size == null || size <= 0) {
            return DEFAULT_CHUNK_SIZE;
        }
        return size;
    }

    /**
     * 获取 overlap 配置。
     *
     * @return overlap 大小
     */
    private int getChunkOverlap() {
        Integer overlap = properties.getChunking().getDefaultOverlap();
        if (overlap == null || overlap < 0) {
            return DEFAULT_OVERLAP;
        }
        return overlap;
    }
    
    /**
     * 对文本做标准化处理。
     *
     * <p>当前目标不是做复杂清洗，而是：
     * <ul>
     *     <li>统一换行符</li>
     *     <li>压缩噪声空白</li>
     *     <li>保留段落边界</li>
     * </ul>
     *
     * @param content 原始文本
     * @return 标准化后的文本
     */
    private String normalizeContent(String content) {
        return content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\t", " ")
                .replaceAll("[ \\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 将文本拆分为段落列表。
     *
     * <p>当前阶段采用最稳定的段落识别方式：
     * 按连续空行拆段。
     *
     * @param content 标准化后的文本
     * @return 段落列表
     */
    private List<String> splitToParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return paragraphs;
        }

        // 1. 按连续空行拆分段落
        String[] rawParagraphs = content.split("\\n\\s*\\n");
        for (String rawParagraph : rawParagraphs) {
            if (rawParagraph == null) {
                continue;
            }

            // 2. 去除段落首尾空白，并过滤掉空段落
            String paragraph = rawParagraph.trim();
            if (paragraph.isBlank()) {
                continue;
            }

            paragraphs.add(paragraph);
        }

        return paragraphs;
    }


    /**
     * 按最终版策略组装 chunk。
     *
     * <p>策略：
     * <ul>
     *     <li>多个短段落优先拼装为一个 chunk</li>
     *     <li>超长段落不直接字符切，而是转入句子优先切分</li>
     * </ul>
     *
     * @param paragraphs 段落列表
     * @param chunkSize  分块大小
     * @param overlap    overlap 大小，仅在最终字符兜底切分时使用
     * @return chunk 文本列表
     */
    private List<String> assembleChunks(List<String> paragraphs, int chunkSize, int overlap) {
        List<String> chunkContents = new ArrayList<>();
        
        // 1. 若段落列表为空，直接返回空结果
        if (paragraphs == null || paragraphs.isEmpty()) {
            return chunkContents;
        }
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs){
            // 2. 若段落本身超长，则优先处理当前正在积累的chunk，再单独切这个长段
            if (paragraph.length() > chunkSize){
                flushCurrentChunk(chunkContents, currentChunk);

                List<String> fallbackChunks = splitOversizeParagraph(paragraph, chunkSize, overlap);
                chunkContents.addAll(fallbackChunks);
                continue;
            }
            
            // 3. 先尝试把当前段落追加到当前 chunk 中。
            String candidate = currentChunk.isEmpty() 
                    ? paragraph 
                    : currentChunk + "\n\n" + paragraph;
            
            // 4. 若追加后仍在大小范围内，则继续合并，优先保留段落语义完整性。
            if (candidate.length() <= chunkSize) {
                if (!currentChunk.isEmpty()) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
                continue;
            }
            
            // 5. 若追加后超长，则先落盘当前chunk，再以当前段落开启新chunk
            flushCurrentChunk(chunkContents, currentChunk);
            currentChunk.append(paragraph);
        }
        
        // 6. 循环结束后，把最后一个未罗盘chunk 写入结果
        flushCurrentChunk(chunkContents,currentChunk);
        
        return chunkContents;
    }

    /**
     * 对超长段落执行“句子优先 + 字符兜底”的最终版切分。
     *
     * <p>这是分块层最终版的关键步骤：
     * <ul>
     *     <li>不再对超长段直接滑窗字符切分</li>
     *     <li>先拆成句子单元</li>
     *     <li>再把句子单元重新拼装成 chunk</li>
     *     <li>仅当某个句子本身仍超长时，再按字符数最终兜底</li>
     * </ul>
     *
     * @param paragraph 超长段落
     * @param chunkSize 分块大小
     * @param overlap   overlap 大小，仅最终字符兜底时使用
     * @return 切分后的 chunk 文本列表
     */
    private List<String> splitOversizeParagraph(String paragraph, int chunkSize, int overlap) {
        log.info("[KB][CHUNK][PARAGRAPH] 检测到超长段落，开始执行句子优先切分 - paragraphLength={}, chunkSize={}, overlap={}",
                paragraph.length(), chunkSize, overlap);
        // 1. 先将超长段拆成句子级单元。
        List<String> sentences = splitToSentences(paragraph);
        
        log.info("[KB][CHUNK][PARAGRAPH] 超长段句子切分完成 - paragraphLength={}, sentenceCount={}",
                paragraph.length(), sentences.size());
        
        List<String> chunkContents = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            // 2. 若单句本身已超长，则说明句边界仍不足以承载完整切分，此时才进入字符兜底。
            if(sentence.length() > chunkSize){
                flushCurrentChunk(chunkContents, currentChunk);
                
                List<String> fallbackChunks = splitOversizedSentence(sentence, chunkSize, overlap);
                chunkContents.addAll(fallbackChunks);
                continue;
            }

            // 3. 尝试将当前句子拼入当前 chunk。
            String candidate = currentChunk.isEmpty() 
                    ? sentence 
                    : currentChunk + "\n\n" + sentence;

            // 4. 若追加后仍不超长，则继续累积，尽量保持句子语义完整。
            if (candidate.length() <= chunkSize) {
                currentChunk.append(sentence);
                continue;
            }

            // 5. 若追加后超长，则先落盘当前 chunk，再以当前句开启新 chunk。
            flushCurrentChunk(chunkContents, currentChunk);
            currentChunk.append(sentence);
        }
        
        // 6. 最后落盘剩余句子累积。
        flushCurrentChunk(chunkContents, currentChunk);

        log.info("[KB][CHUNK][PARAGRAPH] 超长段最终切分完成 - paragraphLength={}, chunkCount={}",
                paragraph.length(), chunkContents.size());

        return chunkContents;
    }
    
    /**
     * 将段落切分为句子级单元。
     *
     * <p>当前规则采用轻量句边界识别：
     * 按以下标记优先断句：
     * <ul>
     *     <li>中文句号：。</li>
     *     <li>中文问号：？</li>
     *     <li>中文感叹号：！</li>
     *     <li>中文分号：；</li>
     *     <li>英文句号：.</li>
     *     <li>英文问号：?</li>
     *     <li>英文感叹号：!</li>
     *     <li>英文分号：;</li>
     *     <li>换行：\n</li>
     * </ul>
     *
     * <p>这里不追求复杂 NLP 断句，只追求稳定、可控、可维护。
     *
     * @param paragraph 段落文本
     * @return 句子单元列表
     */
    private List<String> splitToSentences(String paragraph) {
        List<String> sentences = new ArrayList<>();

        if (paragraph == null || paragraph.isBlank()) {
            return sentences;
        }

        StringBuilder currentSentence = new StringBuilder();
        for (int i = 0; i < paragraph.length(); i++) {
            char currentChar = paragraph.charAt(i);
            currentSentence.append(currentChar);

            // 1. 命中句边界字符时，将当前句子单元落盘。
            if (isSentenceBoundary(currentChar)) {
                String sentence = currentSentence.toString().trim();
                if (!sentence.isBlank()) {
                    sentences.add(sentence);
                }
                // 重置当前句子累积，准备开始下一个句子单元的积累。
                currentSentence.setLength(0);
            }
        }

        // 2. 处理末尾未以句边界结束的残余文本。
        if (!currentSentence.isEmpty()) {
            String sentence = currentSentence.toString().trim();
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
        }

        return sentences;
    }

    /**
     * 判断字符是否为句边界。
     *
     * @param currentChar 当前字符
     * @return true 表示句边界
     */
    private boolean isSentenceBoundary(char currentChar) {
        return currentChar == '。'
                || currentChar == '？'
                || currentChar == '！'
                || currentChar == '；'
                || currentChar == '.'
                || currentChar == '?'
                || currentChar == '!'
                || currentChar == ';'
                || currentChar == '\n';
    }

    /**
     * 对超长句子执行最终字符兜底切分。
     *
     * <p>这是最终版里唯一允许按字符数切分的场景。
     *
     * <p>此时才保留有限 overlap，用于减少边界信息损失。
     *
     * @param sentence  超长句子
     * @param chunkSize 分块大小
     * @param overlap   overlap 大小
     * @return 切分后的 chunk 文本列表
     */
    private List<String> splitOversizedSentence(String sentence, int chunkSize, int overlap) {
        List<String> chunkContents = new ArrayList<>();
        int start = 0;
        int length = sentence.length();

        log.info("[KB][CHUNK][SENTENCE] 检测到超长句子，开始执行字符兜底切分 - sentenceLength={}, chunkSize={}, overlap={}",
                length, chunkSize, overlap);

        // 1. 按 chunkSize 切分句子
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunk = sentence.substring(start, end).trim();

            // 2. 将当前 chunk 落盘
            if (!chunk.isBlank()) {
                chunkContents.add(chunk);
            }

            // 3. 更新 start 指针，准备开始下一个 chunk 的积累。
            if (end == length) {
                break;
            }

            // overlap 只在最终兜底场景保留。
            start = Math.max(end - overlap, start + 1);
        }

        log.info("[KB][CHUNK][SENTENCE] 超长句子字符兜底切分完成 - sentenceLength={}, fallbackChunkCount={}",
                length, chunkContents.size());

        return chunkContents;
    }
    
    /**
     * 将当前正在积累的 chunk 落盘。
     *
     * @param chunkContents chunk 文本列表
     * @param currentChunk  当前正在积累的 chunk
     */
    private void flushCurrentChunk(List<String> chunkContents, StringBuilder currentChunk) {
        if (currentChunk == null || currentChunk.isEmpty()) {
            return;
        }

        // 1. 将当前 chunk 落盘
        String chunk = currentChunk.toString().trim();
        if (!chunk.isBlank()) {
            chunkContents.add(chunk);
        }

        // 2. 重置当前 chunk
        currentChunk.setLength(0);
    }

    /**
     * 构造 KbChunk 实体。
     *
     * @param documentId   文档 ID
     * @param chunkIndex   chunk 下标
     * @param chunkContent chunk 内容
     * @return KbChunk 实体
     */
    private KbChunk buildChunk(Long documentId, int chunkIndex, String chunkContent) {
        return KbChunk.builder()
                .documentId(documentId)
                .chunkIndex(chunkIndex)
                .content(chunkContent)
                .charCount(chunkContent.length())
                .tokenCount(estimateTokenCount(chunkContent))
                .enabled(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 估算 token 数。
     *
     * <p>当前仍保留轻量估算策略，不在本轮扩展真实 tokenizer。
     *
     * @param text 文本内容
     * @return token 数估算值
     */
    private int estimateTokenCount(String text) {
        return Math.max(1, text.length() / 2);
    }
}
