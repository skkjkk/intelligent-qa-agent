package com.jujiu.agent.service.kb.impl;

import com.jujiu.agent.common.result.ChunkSearchResult;
import com.jujiu.agent.model.dto.response.CitationResponse;
import com.jujiu.agent.service.kb.RetrievalResultOrganizer;
import com.jujiu.agent.service.kb.model.OrganizedRetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 默认检索结果整理器实现。
 *
 * <p>这是第一阶段的“检索结果整理层”核心实现，负责把原始检索结果统一整理为：
 * <ul>
 *     <li>最终上下文</li>
 *     <li>最终 citation</li>
 *     <li>最终保留候选</li>
 *     <li>基础空结果原因</li>
 * </ul>
 *
 * <p>当前版本坚持“小步可收口，但结构按最终版方向搭建”的原则：
 * <ul>
 *     <li>不修改现有 VectorSearchService 的职责</li>
 *     <li>不直接引入 rerank / rewrite / 新依赖</li>
 *     <li>先把去重、裁剪、citation/snippet/context 统一起来</li>
 * </ul>
 *
 * <p>第一阶段已覆盖能力：
 * <ul>
 *     <li>原始结果为空时的统一空结构返回</li>
 *     <li>按 chunkId 做基础去重</li>
 *     <li>按文档限制最大保留 chunk 数</li>
 *     <li>按分数重新排序</li>
 *     <li>统一生成 snippet / citation / context</li>
 * </ul>
 *
 * <p>第二阶段建议在本类内继续扩展：
 * <ul>
 *     <li>近重复 chunk 合并</li>
 *     <li>相邻 chunk 拼接</li>
 *     <li>query-aware snippet 截取</li>
 *     <li>更细空结果原因分类</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/17
 */
@Service
@Slf4j
public class DefaultRetrievalResultOrganizer implements RetrievalResultOrganizer {
    /** 单个文档最多保留的 chunk 数量。 */
    private static final int MAX_CHUNKS_PER_DOCUMENT = 3;

    /** snippet 默认最大长度。 */
    private static final int MAX_SNIPPET_LENGTH = 220;

    /** query-aware snippet 向左保留的窗口长度。 */
    private static final int SNIPPET_LEFT_WINDOW = 50;

    /** query-aware snippet 向右保留的窗口长度。 */
    private static final int SNIPPET_RIGHT_WINDOW = 170;

    /** 近重复判定时，标准化后最短比较长度。 */
    private static final int MIN_CONTENT_LENGTH_FOR_NEAR_DUPLICATE = 40;

    /** 同文档近重复判定阈值。 */
    private static final double SAME_DOCUMENT_NEAR_DUPLICATE_THRESHOLD = 0.82D;

    /** 跨文档近重复判定阈值。 */
    private static final double CROSS_DOCUMENT_NEAR_DUPLICATE_THRESHOLD = 0.92D;

    /** 空结果原因：存在有效结果。 */
    private static final String EMPTY_REASON_NONE = "NONE";

    /** 空结果原因：原始检索结果为空。 */
    private static final String EMPTY_REASON_NO_RAW_RESULTS = "NO_RAW_RESULTS";

    /** 空结果原因：整理后全部被过滤。 */
    private static final String EMPTY_REASON_ALL_FILTERED = "ALL_FILTERED_AFTER_ORGANIZE";
    
    /** 最终最多保留的结果数量。 */
    private static final int MAX_FINAL_RESULTS = 5;

    /** 最终上下文最大长度。 */
    private static final int MAX_CONTEXT_LENGTH = 1600;

    /**
     * 对原始检索结果执行统一整理。
     *
     * <p>当前流程：
     * <ol>
     *     <li>空值兜底</li>
     *     <li>基础去重</li>
     *     <li>每文档结果数限制</li>
     *     <li>按分数排序并重排 rank</li>
     *     <li>统一构造 citations</li>
     *     <li>统一构造 context</li>
     * </ol>
     *
     * @param rawResults 原始检索结果
     * @param question   用户原始问题
     * @return 整理后的统一结果
     */
    @Override
    public OrganizedRetrievalResult organize(List<ChunkSearchResult> rawResults, String question) {
        int rawCount = rawResults == null ? 0 : rawResults.size();
        
        log.info("[KB][RETRIEVAL][ORGANIZE] 开始整理检索结果 - rawResultCount={}, questionLength={}",
                rawCount,
                question == null ? 0 : question.length());
        
        // 1. 若原始检索结果为空，直接返回统一空结构，避免上层链路重复判空和各自组装。
        if (rawResults == null || rawResults.isEmpty()) {
            log.info("[KB][RETRIEVAL][ORGANIZE] 原始检索结果为空，返回统一空结构 - emptyReason={}",
                    EMPTY_REASON_NO_RAW_RESULTS);
            
            return OrganizedRetrievalResult.builder()
                    .finalResults(List.of())
                    .citations(List.of())
                    .context("")
                    .rawResultCount(0)
                    .finalResultCount(0)
                    .emptyReason(EMPTY_REASON_NO_RAW_RESULTS)
                    .build();
        }

        // 2. 先按 chunkId 执行基础去重，去掉完全重复的检索结果。
        List<ChunkSearchResult> deduplicatedResults = deduplicateByChunkId(rawResults);
        
        // 3. 再执行近重复去重，压缩内容高度相似但 chunkId 不同的候选结果。
        List<ChunkSearchResult> nearDeduplicatedResults = deduplicateNearDuplicateContent(deduplicatedResults);
        
        // 4. 限制同一文档最多保留的 chunk 数，避免单文档结果霸榜，影响上下文多样性。
        List<ChunkSearchResult> limitedResults = limitChunksPerDocument(nearDeduplicatedResults);

        // 5. 对整理后的候选结果按得分重排。
        List<ChunkSearchResult> rerankedResults = sortAndReRank(limitedResults);

        // 6. 在最终输出前再做一次全局数量裁剪，避免最终证据集过胖。
        List<ChunkSearchResult> finalResults = limitFinalResults(rerankedResults, MAX_FINAL_RESULTS);

        // 7. 若整理后没有结果，返回统一空结构并记录原因。
        if (finalResults.isEmpty()) {
            log.info("[KB][RETRIEVAL][ORGANIZE] 检索结果在整理后为空 - rawResultCount={}, emptyReason={}",
                    rawCount, EMPTY_REASON_ALL_FILTERED);

            return OrganizedRetrievalResult.builder()
                    .finalResults(List.of())
                    .citations(List.of())
                    .context("")
                    .rawResultCount(rawCount)
                    .finalResultCount(0)
                    .emptyReason(EMPTY_REASON_ALL_FILTERED)
                    .build();
        }

        // 8. 基于最终候选统一生成 citation。
        List<CitationResponse> citations = buildCitations(finalResults, question);

// 9. 基于同一批最终候选构造有长度预算的 context。
        String context = buildContextWithBudget(finalResults, question, MAX_CONTEXT_LENGTH);

        log.info("[KB][RETRIEVAL][ORGANIZE] 检索结果整理完成 - rawResultCount={}, finalResultCount={}, citationCount={}, contextLength={}",
                rawCount, finalResults.size(), citations.size(), context.length());

        return OrganizedRetrievalResult.builder()
                .finalResults(finalResults)
                .citations(citations)
                .context(context)
                .rawResultCount(rawCount)
                .finalResultCount(finalResults.size())
                .emptyReason(EMPTY_REASON_NONE)
                .build();
    }

    /**
     * 对内容高度相似的 chunk 执行近重复去重。
     *
     * <p>第一阶段只按 chunkId 去重，还不足以处理这种情况：
     * <ul>
     *     <li>同一文档相邻 chunk 内容高度重叠</li>
     *     <li>不同召回源返回了内容几乎相同的 chunk</li>
     *     <li>同一段原文被不同 chunk 切分策略重复覆盖</li>
     * </ul>
     *
     * <p>当前阶段采用“轻量但稳定”的近重复判定策略：
     * <ul>
     *     <li>先做文本标准化</li>
     *     <li>再按 token 集合计算 Jaccard 相似度</li>
     *     <li>相似度达到阈值则视为近重复</li>
     * </ul>
     *
     * <p>该策略不追求语义级最优，只追求：
     * <ul>
     *     <li>实现简单</li>
     *     <li>无需新增依赖</li>
     *     <li>容易测试</li>
     *     <li>能先解决大部分重复证据问题</li>
     * </ul>
     *
     * @param results 已按 chunkId 去重后的结果
     * @return 近重复去重后的结果
     */
    private List<ChunkSearchResult> deduplicateNearDuplicateContent(List<ChunkSearchResult> results) {
        log.info("[KB][RETRIEVAL][NEAR_DEDUP] 开始执行近重复内容去重 - inputCount={}",
                results == null ? 0 : results.size());
        
        // 1. 空结果直接返回，避免后续无意义遍历
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        
        List<ChunkSearchResult> deduplicatedResults = new ArrayList<>();
        int droppedCount = 0;
        for (ChunkSearchResult current  : results) {
            // 2. 若当前结果为空，直接跳过。
            if (current == null) {
                droppedCount++;
                continue;
            }
            
            boolean duplicate = false;
            
            // 3. 与已经保留的结果逐个比较，只要命中一个近重复就丢弃
            for (ChunkSearchResult existing : deduplicatedResults) {
                if (isNearDuplicate(existing, current)) {
                    duplicate = true;
                    droppedCount++;

                    log.debug("[KB][RETRIEVAL][NEAR_DEDUP] 检测到近重复 chunk，已丢弃 - keptChunkId={}, droppedChunkId={}, keptDocumentId={}, droppedDocumentId={}",
                            existing.getChunkId(),
                            current.getChunkId(),
                            existing.getDocumentId(),
                            current.getDocumentId());
                    break;
                }
            }
            
            // 4. 若未命中近重复，则保留当前结果
            if (!duplicate) {
                deduplicatedResults.add(current);
            }
        }
        log.info("[KB][RETRIEVAL][NEAR_DEDUP] 近重复内容去重完成 - inputCount={}, outputCount={}, droppedCount={}",
                results.size(), deduplicatedResults.size(), droppedCount);
        
        return deduplicatedResults;
    }

    /**
     * 判断两个检索结果是否为近重复内容。
     *
     * <p>当前策略遵循“保守去重”原则：
     * <ul>
     *     <li>仅处理明显高度相似的重复证据</li>
     *     <li>尽量避免误删带有补充信息的 chunk</li>
     *     <li>同文档判重可以略宽松，跨文档判重更严格</li>
     * </ul>
     *
     * @param left  左侧结果
     * @param right 右侧结果
     * @return true 表示两者应视为近重复
     */
    private boolean isNearDuplicate(ChunkSearchResult left, ChunkSearchResult right) {
        // 1. 任一侧为空时，不视为近重复。
        if (left == null || right == null) {
            return false;
        }
        
        // 2. 若chunkId 相同，原则上已在上一阶段去重，这里直接视为重复
        if (left.getChunkId() != null && left.getChunkId().equals(right.getChunkId())) {
            return true;
        }
        
        // 3. 对文本做标准化，去除大小写和多余空白影响。
        String leftContent  = normalizeText(left.getContent());
        String rightContent  = normalizeText(right.getContent());

        // 4. 若标准化后任一内容过短，则不做近重复判定，避免误杀短文本。
        if (leftContent.length() < MIN_CONTENT_LENGTH_FOR_NEAR_DUPLICATE
                || rightContent.length() < MIN_CONTENT_LENGTH_FOR_NEAR_DUPLICATE) {
            return false;
        }
        
        // 5. 若标准化后文本完全一致，则直接视为近重复
        if (leftContent.equals(rightContent)) {
            return true;
        }
        
        // 6. 基于token集合计算Jaccard相似度，若达到阈值则视为近重复
        double similarity  = calculateJaccardSimilarity(leftContent, rightContent);
        
        // 7. 同文档结果更容易因为切分或 overlap 产生重复，因此阈值略低。
        if (Objects.equals(left.getDocumentId(), right.getDocumentId())) {
            return similarity >= SAME_DOCUMENT_NEAR_DUPLICATE_THRESHOLD;
        }
        
        // 8. 跨文档结果更严格，阈值略高。
        return similarity >= CROSS_DOCUMENT_NEAR_DUPLICATE_THRESHOLD;
    }

    /**
     * 对内容做轻量标准化。
     *
     * @param content 原始内容
     * @return 标准化后的内容
     */
    private String normalizeText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        return content.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .replace("，", " ")
                .replace("。", " ")
                .replace("？", " ")
                .replace("！", " ")
                .replace(",", " ")
                .replace(".", " ")
                .replace("?", " ")
                .replace("!", " ")
                .trim();
    }

    /**
     * 计算两个文本的 Jaccard 相似度。
     *
     * @param leftContent  左侧文本
     * @param rightContent 右侧文本
     * @return 相似度
     */
    private double calculateJaccardSimilarity(String leftContent, String rightContent) {
        // 1. 拆分为token集合，便于做轻量级和相似度计算
        List<String> leftTokens = splitToTokens(leftContent);
        List<String> rightTokens = splitToTokens(rightContent);

        // 2. 任一侧为空时，直接视为不相似。
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0D;
        }

        // 3. 转成集合，去除重复 token 对相似度的干扰。
        Set<String> leftSet  = new  HashSet<>(leftTokens);
        Set<String> rightSet  = new HashSet<>(rightTokens);
        
        // 4. 计算交集，找出两个集合共有的 token。
        Set<String> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        
        // 5. 计算并集，找出两个集合中所有的 token。
        Set<String> union = new HashSet<>(leftSet);
        union.addAll(rightSet);
        
        // 6. 防御性过滤空并集，避免除零错误。
        if(union.isEmpty()) {
            return 0D;
        }
        
        // 7. 返回 Jaccard 相似度。
        return (double) intersection.size() / union.size();
    }

    /**
     * 将文本拆分为轻量 token 列表。
     *
     * @param content 标准化后的内容
     * @return token 列表
     */
    private List<String> splitToTokens(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(content.split("\\s+"))
                .filter(token -> token != null && !token.isBlank())
                .filter(token -> token.length() >= 2)
                .toList();
    }

    /**
     * 按 chunkId 执行基础去重。
     *
     * <p>第一阶段先采用最稳妥的策略：
     * 同一个 chunkId 只保留首次出现的结果。
     *
     * <p>这样做的好处是：
     * <ul>
     *     <li>不引入复杂近重复算法</li>
     *     <li>足以先解决融合后重复 chunk 的明显问题</li>
     *     <li>实现简单，测试容易锁住</li>
     * </ul>
     *
     * @param rawResults 原始结果
     * @return 去重后的结果
     */
    private List<ChunkSearchResult> deduplicateByChunkId(List<ChunkSearchResult> rawResults) {
        log.info("[KB][RETRIEVAL][DEDUP] 开始按 chunkId 去重 - inputCount={}", rawResults.size());
        
        // 使用 LinkedHashMap 保持原始顺序，避免无意义抖动。
        Map<Long, ChunkSearchResult> deduplicatedMap  = new LinkedHashMap<>();
        int skippedCount = 0;

        for (ChunkSearchResult rawResult : rawResults) {
            // 1. 防御性过滤空对象，避免后续空指针。
            if (rawResult == null) {
                skippedCount++;
                continue;
            }

            // 2. chunkId 为空的结果不具备稳定去重键，当前阶段直接跳过。
            if (rawResult.getChunkId() == null) {
                skippedCount++;
                log.warn("[KB][RETRIEVAL][DEDUP] 检测到 chunkId 为空的检索结果，已跳过 - documentId={}, title={}",
                        rawResult.getDocumentId(), rawResult.getDocumentTitle());
                continue;
            }

            // 3. 若该 chunk 已出现，则忽略后续重复项。
            if (deduplicatedMap.containsKey(rawResult.getChunkId())) {
                skippedCount++;
                log.debug("[KB][RETRIEVAL][DEDUP] 检测到重复 chunk，已忽略 - chunkId={}, documentId={}",
                        rawResult.getChunkId(), rawResult.getDocumentId());
                continue;
            }

            deduplicatedMap.putIfAbsent(rawResult.getChunkId(), rawResult);
        }
        List<ChunkSearchResult> deduplicatedResults = new ArrayList<>(deduplicatedMap.values());

        log.info("[KB][RETRIEVAL][DEDUP] 按 chunkId 去重完成 - inputCount={}, outputCount={}, skippedCount={}",
                rawResults.size(), deduplicatedResults.size(), skippedCount);

        return deduplicatedResults;
    }

    /**
     * 限制单个文档最多保留的 chunk 数量。
     *
     * <p>这样做的目标是：
     * <ul>
     *     <li>避免单个文档垄断上下文</li>
     *     <li>提升回答证据来源的多样性</li>
     *     <li>降低重复信息和 token 浪费</li>
     * </ul>
     *
     * @param results 去重后的候选结果
     * @return 裁剪后的结果
     */
    private List<ChunkSearchResult> limitChunksPerDocument(List<ChunkSearchResult> results) {
        log.info("[KB][RETRIEVAL][LIMIT] 开始限制单文档 chunk 数量 - inputCount={}, maxPerDocument={}",
                results.size(), DefaultRetrievalResultOrganizer.MAX_CHUNKS_PER_DOCUMENT);
        List<ChunkSearchResult> limitedResults = new ArrayList<>();
        Map<Long, Integer> documentCounter = new HashMap<>();
        int droppedCount = 0;

        for (ChunkSearchResult result : results) {
            // 1. 文档 ID 为空时无法归档统计，当前阶段直接跳过，避免不稳定行为。
            if (result.getDocumentId() == null) {
                droppedCount++;
                log.warn("[KB][RETRIEVAL][LIMIT] 检测到 documentId 为空的结果，已跳过 - chunkId={}",
                        result.getChunkId());
                continue;
            }
            
            // 2. 获取当前文档已保留的 chunk 数量。
            int currentCount = documentCounter.getOrDefault(result.getDocumentId(), 0);

            // 3. 若当前文档已达到上限，则丢弃后续结果。
            if (currentCount >= DefaultRetrievalResultOrganizer.MAX_CHUNKS_PER_DOCUMENT) {
                droppedCount++;
                log.debug("[KB][RETRIEVAL][LIMIT] 文档结果达到上限，丢弃后续 chunk - documentId={}, chunkId={}, currentCount={}",
                        result.getDocumentId(), result.getChunkId(), currentCount);
                continue;
            }
            
            limitedResults.add(result);
            documentCounter.put(result.getDocumentId(), currentCount + 1);
        }
        
        log.info("[KB][RETRIEVAL][LIMIT] 单文档 chunk 数量限制完成 - inputCount={}, outputCount={}, droppedCount={}, documentCount={}",
                results.size(), limitedResults.size(), droppedCount, documentCounter.size());
        
        return limitedResults;
    }

    /**
     * 按得分重新排序并重排 rank。
     *
     * <p>由于前面经历了去重和裁剪，原始 rank 已不再可靠，
     * 因此需要重新统一排序，确保：
     * <ul>
     *     <li>citation.rank 一致</li>
     *     <li>context 中的编号一致</li>
     *     <li>日志与 trace 顺序一致</li>
     * </ul>
     *
     * @param results 待排序结果
     * @return 重新排序并更新 rank 的结果
     */
    private List<ChunkSearchResult> sortAndReRank(List<ChunkSearchResult> results) {
        log.info("[KB][RETRIEVAL][RERANK] 开始按得分重排结果 - inputCount={}", results.size());

        // 1. 复制一份列表，避免直接修改上游传入集合。
        List<ChunkSearchResult> sortedResults = new ArrayList<>(results);

        // 2. 按 score 降序排序；score 为空的结果排到后面。
        sortedResults.sort(Comparator.comparing(
                ChunkSearchResult::getScore,
                Comparator.nullsLast(Double::compareTo)
        ).reversed());

        // 3. 统一重排 rank，确保后续所有消费端口径一致。
        for (int i = 0; i < sortedResults.size(); i++) {
            sortedResults.get(i).setRank(i + 1);
        }

        log.info("[KB][RETRIEVAL][RERANK] 结果重排完成 - outputCount={}", sortedResults.size());

        return sortedResults;
    }

    /**
     * 统一构造 citation 列表。
     *
     * <p>第一阶段暂不做复杂 query-aware snippet，
     * 先把“统一入口”建立起来，后续你只需要在这里升级 snippet 策略即可。
     *
     * @param finalResults 整理后的最终候选
     * @param question     用户问题
     * @return 引用列表
     */
    private List<CitationResponse> buildCitations(List<ChunkSearchResult> finalResults, String question) {
        log.info("[KB][RETRIEVAL][CITATION] 开始构造引用列表 - finalResultCount={}", finalResults.size());

        List<CitationResponse> citations = new ArrayList<>();

        for (ChunkSearchResult finalResult  : finalResults) {
            // 1. 对每个最终候选统一生成 snippet，确保 citation 和 context 共享同一截取逻辑。
            String snippet = buildSnippet(finalResult.getContent(), question);

            citations.add(CitationResponse.builder()
                    .chunkId(finalResult.getChunkId())
                    .documentId(finalResult.getDocumentId())
                    .documentTitle(finalResult.getDocumentTitle())
                    .snippet(snippet)
                    .score(finalResult.getScore())
                    .rank(finalResult.getRank())
                    .build());
        }
        log.info("[KB][RETRIEVAL][CITATION] 引用列表构造完成 - citationCount={}", citations.size());

        return citations;
    }

    /**
     * 构造带长度预算的最终上下文。
     *
     * <p>目标：
     * <ul>
     *     <li>上下文只来自最终证据集</li>
     *     <li>避免 context 无限制变长</li>
     *     <li>优先保留前排高价值证据</li>
     * </ul>
     *
     * @param finalResults      最终候选结果
     * @param question          用户问题
     * @param maxContextLength  上下文最大长度
     * @return 上下文文本
     */
    private String buildContextWithBudget(List<ChunkSearchResult> finalResults, 
                                          String question, 
                                          int maxContextLength) {
        log.info("[KB][RETRIEVAL][CONTEXT] 开始构造带预算的最终上下文 - finalResultCount={}, maxContextLength={}",
                finalResults == null ? 0 : finalResults.size(), maxContextLength);
        
        if (finalResults == null || finalResults.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < finalResults.size(); i++) {
            ChunkSearchResult finalResult = finalResults.get(i);
            String snippet = buildSnippet(finalResult.getContent(), question);

            String block = "["
                    + (i + 1)
                    + "] "
                    + (finalResult.getDocumentTitle() == null ? "未命名文档" : finalResult.getDocumentTitle())
                    + "\n"
                    + snippet
                    + "\n\n";

            // 若追加后超过预算，则停止，优先保留高排位证据。
            if (!builder.isEmpty() && builder.length() + block.length() > maxContextLength) {
                log.info("[KB][RETRIEVAL][CONTEXT] 上下文达到预算上限，停止追加 - currentLength={}, nextBlockLength={}, maxContextLength={}",
                        builder.length(), block.length(), maxContextLength);
                break;
            }

            // 若当前为空但首块本身已超预算，仍允许保留首块，避免 context 为空。
            if (builder.isEmpty() || builder.length() + block.length() <= maxContextLength) {
                builder.append(block);
            }
        }

        String context = builder.toString();

        log.info("[KB][RETRIEVAL][CONTEXT] 带预算上下文构造完成 - contextLength={}", context.length());

        return context;
    }

    /**
     * 构造 query-aware snippet。
     *
     * <p>当前阶段的目标不是做到最复杂的语义抽取，
     * 而是先把“围绕问题命中位置截取证据片段”这件事稳定做好。
     *
     * <p>当前策略：
     * <ul>
     *     <li>若内容较短，则直接返回</li>
     *     <li>若 query 在内容中有直接命中，则围绕命中位置截取窗口</li>
     *     <li>若 query 无直接命中，则回退到简单截断</li>
     * </ul>
     *
     * <p>这样做的收益：
     * <ul>
     *     <li>citation 更像证据，而不是整块原文前缀</li>
     *     <li>上下文更聚焦</li>
     *     <li>能明显减少“明明命中了，但 snippet 看不出来为什么命中”的问题</li>
     * </ul>
     *
     * @param content  原始 chunk 内容
     * @param question 用户问题
     * @return snippet
     */
    private String buildSnippet(String content, String question) {
        // 1. 内容为空或空白时，直接返回空串，避免无意义空指针判断外溢。
        if (content == null || content.isBlank()) {
            log.debug("[KB][RETRIEVAL][SNIPPET] 原始内容为空，返回空 snippet");
            return "";
        }

        // 2. 对原始内容和问题做基础标准化处理。
        String normalizedContent = content.trim();
        String normalizedQuestion = question == null ? "" : question.trim();
        
        // 3. 若内容本身较短，则直接返回，无需额外截断。
        if (normalizedContent.length() <= MAX_SNIPPET_LENGTH){
            log.debug("[KB][RETRIEVAL][SNIPPET] 内容长度未超过上限，直接返回 - contentLength={}",
                    normalizedContent.length());
            return normalizedContent;
        }

        // 4. 优先尝试围绕 query 命中位置截取窗口。
        String queryAwareSnippet = buildQueryAwareSnippet(normalizedContent, normalizedQuestion);
        if (queryAwareSnippet != null && !queryAwareSnippet.isBlank()){
            log.debug("[KB][RETRIEVAL][SNIPPET] 已按 query 命中位置生成 snippet - snippetLength={}, questionLength={}",
                    queryAwareSnippet.length(),
                    normalizedQuestion.length());
            return queryAwareSnippet;
        }
        
        // 5. 若没有命中 query，则回退到简单前缀截断，保证行为稳定可预期。
        String fallbackSnippet = normalizedContent.substring(0, MAX_SNIPPET_LENGTH) + "...";

        log.debug("[KB][RETRIEVAL][SNIPPET] 未命中 query，回退为前缀截断 - originalLength={}, snippetLength={}, questionLength={}",
                normalizedContent.length(),
                fallbackSnippet.length(),
                normalizedQuestion.length());

        return fallbackSnippet;
    }
    
    /**
     * 构造围绕 query 命中位置的 snippet。
     *
     * @param content  原始内容
     * @param question 用户问题
     * @return 若命中则返回 query-aware snippet，否则返回 null
     */
    private String buildQueryAwareSnippet(String content, String question) {
        // 1. 问题为空时无法做 query-aware 截取，直接返回 null。
        if (question == null || question.isEmpty()) {
            return null;
        }
        
        // 2. 先尝试直接查找完整问题在内容中的命中位置
        int hitIndex = content.toLowerCase().indexOf(question.toLowerCase());
        
        // 3. 若完整问题未命中，则退化为尝试命中问题中的关键词
        if (hitIndex < 0){
            List<String> queryTerms = splitQuestionTerms(question);
            for (String term : queryTerms) {
                hitIndex = content.toLowerCase().indexOf(term.toLowerCase());
                if (hitIndex >= 0) {
                    break;
                }
            }
        }
        
        // 4. 若仍未命中，则返回null，让外层回退到简单阶段。
        if (hitIndex < 0){
            return null;
        }
        
        // 5. 围绕命中位置截取受控窗口，尽量把证据集中展示出来
        int start = Math.max(0,hitIndex - SNIPPET_LEFT_WINDOW);
        int end = Math.min(content.length(),hitIndex + SNIPPET_RIGHT_WINDOW);

        String snippet = content.substring(start, end);
        
        // 6. 若不是从文本开头截取，则补前缀省略号，表示中间截取
        if (start > 0){
            snippet = "..." + snippet;
        }
        
        // 7. 若不是截到文本末尾，则补后缀省略号
        if (end < content.length()){
            snippet = snippet + "...";
        }
        return snippet;
    }

    /**
     * 对问题做轻量拆词，用于 query-aware snippet 的降级命中。
     *
     * @param question 用户问题
     * @return 拆词结果
     */
    private List<String> splitQuestionTerms(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        String normalized = question.toLowerCase()
                .replace("，", " ")
                .replace("。", " ")
                .replace("？", " ")
                .replace("！", " ")
                .replace(",", " ")
                .replace(".", " ")
                .replace("?", " ")
                .replace("!", " ")
                .trim();

        if (normalized.isEmpty()) {
            return List.of();
        }

        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(item -> item != null && !item.isBlank())
                .filter(item -> item.length() >= 2)
                .distinct()
                .toList();
    }

    /**
     * 对最终结果数量执行全局裁剪。
     *
     * <p>该步骤位于：
     * <ul>
     *     <li>去重之后</li>
     *     <li>单文档限制之后</li>
     *     <li>最终 citation/context 生成之前</li>
     * </ul>
     *
     * <p>目标是把“候选结果”进一步收敛成“最终证据集”，
     * 避免最终返回过多引用、过长上下文和过多噪声结果。
     *
     * @param results     已重排结果
     * @param maxResults  最终最大保留数量
     * @return 裁剪后的最终结果
     */
    private List<ChunkSearchResult> limitFinalResults(List<ChunkSearchResult> results, int maxResults) {
        log.info("[KB][RETRIEVAL][FINAL_LIMIT] 开始执行最终结果裁剪 - inputCount={}, maxResults={}",
                results == null ? 0 : results.size(), maxResults);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<ChunkSearchResult> limitedResults = results.stream()
                .limit(maxResults)
                .toList();

        // 重新赋 rank，确保最终 citations/context 序号一致。
        for (int i = 0; i < limitedResults.size(); i++) {
            limitedResults.get(i).setRank(i + 1);
        }

        log.info("[KB][RETRIEVAL][FINAL_LIMIT] 最终结果裁剪完成 - outputCount={}", limitedResults.size());

        return limitedResults;
    }

}
