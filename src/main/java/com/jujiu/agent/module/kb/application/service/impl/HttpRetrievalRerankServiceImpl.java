package com.jujiu.agent.module.kb.application.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jujiu.agent.module.kb.application.model.ChunkSearchResult;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.application.service.RetrievalRerankService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼文本排序服务实现。
 *
 * <p>当前版本固定适配 DashScope 的文本排序接口：
 * <ul>
 *     <li>endpoint: /api/v1/services/rerank/text-rerank/text-rerank</li>
 *     <li>model: qwen3-vl-rerank</li>
 *     <li>输入格式：input.query / input.documents</li>
 *     <li>输出格式：output.results[].relevance_score</li>
 * </ul>
 *
 * <p>该实现是当前项目的正式版 rerank 客户端，不再走通用占位结构。
 *
 * @author 17644
 * @since 2026/4/19
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "knowledge-base.rerank", name = "enabled", havingValue = "true")
public class HttpRetrievalRerankServiceImpl implements RetrievalRerankService {

    private final RestTemplate restTemplate;
    private final KnowledgeBaseProperties knowledgeBaseProperties;

    public HttpRetrievalRerankServiceImpl(RestTemplate restTemplate,
                                          KnowledgeBaseProperties knowledgeBaseProperties) {
        this.restTemplate = restTemplate;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
    }

    /**
     * 对候选结果执行百炼 rerank。
     *
     * @param question   用户问题
     * @param candidates 候选结果
     * @param topN       最终保留数量
     * @return rerank 后结果
     */
    @Override
    public List<ChunkSearchResult> rerank(String question,
                                          List<ChunkSearchResult> candidates,
                                          Integer topN) {
        if (question == null || question.isBlank() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        KnowledgeBaseProperties.Rerank rerankProperties = knowledgeBaseProperties.getRerank();
        int maxCandidates = resolveMaxCandidates(rerankProperties);
        int finalTopN = resolveTopN(topN, rerankProperties);

        // 先截断候选规模，确保降级和正常路径都基于同一批候选。
        List<ChunkSearchResult> truncatedCandidates = candidates.stream()
                .limit(maxCandidates)
                .toList();

        if (rerankProperties == null
                || rerankProperties.getApiUrl() == null
                || rerankProperties.getApiUrl().isBlank()) {
            log.warn("[KB][RERANK] 百炼 rerank 已启用但未配置 apiUrl，回退使用原候选顺序");
            return fallbackResults(truncatedCandidates, finalTopN);
        }

        if (rerankProperties.getApiKey() == null || rerankProperties.getApiKey().isBlank()) {
            log.warn("[KB][RERANK] 百炼 rerank 已启用但未配置 apiKey，回退使用原候选顺序");
            return fallbackResults(truncatedCandidates, finalTopN);
        }

        List<RerankDocument> documents = truncatedCandidates.stream()
                .map(item -> item.getContent() == null ? "" : item.getContent())
                .map(text -> RerankDocument.builder().text(text).build())
                .toList();

        RerankRequest request = RerankRequest.builder()
                .model(resolveModel(rerankProperties))
                .input(RerankInput.builder()
                        .query(RerankDocument.builder().text(question).build())
                        .documents(documents)
                        .build())
                .parameters(RerankParameters.builder()
                        .returnDocuments(false)
                        .topN(finalTopN)
                        .build())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(rerankProperties.getApiKey());

        try {
            long startTime = System.currentTimeMillis();

            RerankResponse response = restTemplate.postForObject(
                    rerankProperties.getApiUrl(),
                    new HttpEntity<>(request, headers),
                    RerankResponse.class
            );

            long latencyMs = System.currentTimeMillis() - startTime;
            log.info("[KB][RERANK] 百炼 rerank 调用完成 - candidateCount={}, topN={}, latencyMs={}",
                    truncatedCandidates.size(), finalTopN, latencyMs);

            if (response != null && response.getCode() != null && !response.getCode().isBlank()) {
                log.warn("[KB][RERANK] 百炼返回错误 - code={}, message={}",
                        response.getCode(), response.getMessage());
                return fallbackResults(truncatedCandidates, finalTopN);
            }

            if (response == null
                    || response.getOutput() == null
                    || response.getOutput().getResults() == null
                    || response.getOutput().getResults().isEmpty()) {
                log.warn("[KB][RERANK] 百炼 rerank 响应为空，回退使用原候选顺序");
                return fallbackResults(truncatedCandidates, finalTopN);
            }

            Map<Integer, Double> scoreMap = new HashMap<>();
            for (RerankItem item : response.getOutput().getResults()) {
                if (item == null || item.getIndex() == null || item.getRelevanceScore() == null) {
                    continue;
                }
                scoreMap.put(item.getIndex(), item.getRelevanceScore());
            }

            double scoreThreshold = rerankProperties.getScoreThreshold() == null
                    ? 0D
                    : rerankProperties.getScoreThreshold();

            List<ChunkSearchResult> rerankedResults = new ArrayList<>();
            for (int i = 0; i < truncatedCandidates.size(); i++) {
                ChunkSearchResult candidate = truncatedCandidates.get(i);
                Double rerankScore = scoreMap.get(i);

                // 未返回分数或低于阈值的候选不进入最终结果。
                if (rerankScore == null || rerankScore < scoreThreshold) {
                    continue;
                }

                rerankedResults.add(ChunkSearchResult.builder()
                        .chunkId(candidate.getChunkId())
                        .documentId(candidate.getDocumentId())
                        .documentTitle(candidate.getDocumentTitle())
                        .content(candidate.getContent())
                        .score(rerankScore)
                        .rank(0)
                        .build());
            }

            rerankedResults.sort(Comparator.comparing(
                    ChunkSearchResult::getScore,
                    Comparator.nullsLast(Double::compareTo)
            ).reversed());

            if (rerankedResults.isEmpty()) {
                log.info("[KB][RERANK] 百炼 rerank 已执行，但所有候选都低于阈值 - candidateCount={}, topN={}, scoreThreshold={}",
                        truncatedCandidates.size(),
                        finalTopN,
                        scoreThreshold);
                return List.of();
            }

            List<ChunkSearchResult> finalResults = rerankedResults.stream()
                    .limit(finalTopN)
                    .toList();

            for (int i = 0; i < finalResults.size(); i++) {
                finalResults.get(i).setRank(i + 1);
            }

            return finalResults;
        } catch (Exception e) {
            log.error("[KB][RERANK] 百炼 rerank 调用失败，回退使用原候选顺序", e);
            return fallbackResults(truncatedCandidates, finalTopN);
        }
    }

    /**
     * 计算最终 topN。
     *
     * @param topN             调用方传入 topN
     * @param rerankProperties rerank 配置
     * @return 最终 topN
     */
    private int resolveTopN(Integer topN, KnowledgeBaseProperties.Rerank rerankProperties) {
        if (topN != null && topN > 0) {
            return topN;
        }
        if (rerankProperties != null && rerankProperties.getTopN() != null && rerankProperties.getTopN() > 0) {
            return rerankProperties.getTopN();
        }
        return 10;
    }

    /**
     * 计算最大送入 rerank 的候选数。
     *
     * @param rerankProperties rerank 配置
     * @return 最大候选数
     */
    private int resolveMaxCandidates(KnowledgeBaseProperties.Rerank rerankProperties) {
        if (rerankProperties != null
                && rerankProperties.getMaxCandidates() != null
                && rerankProperties.getMaxCandidates() > 0) {
            return rerankProperties.getMaxCandidates();
        }
        return 20;
    }

    /**
     * 解析模型名称。
     *
     * @param rerankProperties rerank 配置
     * @return 最终模型名
     */
    private String resolveModel(KnowledgeBaseProperties.Rerank rerankProperties) {
        if (rerankProperties != null
                && rerankProperties.getModel() != null
                && !rerankProperties.getModel().isBlank()) {
            return rerankProperties.getModel();
        }
        return "qwen3-vl-rerank";
    }

    /**
     * rerank 不可用时的兜底结果。
     *
     * @param candidates 原始候选
     * @param topN       保留数量
     * @return 兜底结果
     */
    private List<ChunkSearchResult> fallbackResults(List<ChunkSearchResult> candidates, int topN) {
        List<ChunkSearchResult> fallbackResults = candidates.stream()
                .limit(topN)
                .toList();

        for (int i = 0; i < fallbackResults.size(); i++) {
            fallbackResults.get(i).setRank(i + 1);
        }
        return fallbackResults;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankRequest {

        /**
         * 百炼模型名称。
         */
        private String model;

        /**
         * 输入体。
         */
        private RerankInput input;

        /**
         * 调用参数。
         */
        private RerankParameters parameters;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankInput {

        /**
         * 查询内容。
         */
        private RerankDocument query;

        /**
         * 候选文档列表。
         */
        private List<RerankDocument> documents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankDocument {

        /**
         * 文本内容。
         *
         * <p>当前知识库只走文本排序，因此固定使用 text 字段。
         */
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankParameters {

        /**
         * 是否返回文档内容。
         */
        @JsonProperty("return_documents")
        private Boolean returnDocuments;

        /**
         * 返回前 N 条结果。
         */
        @JsonProperty("top_n")
        private Integer topN;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankResponse {

        /**
         * 百炼输出结果。
         */
        private RerankOutput output;

        /**
         * 请求唯一标识。
         */
        @JsonProperty("request_id")
        private String requestId;

        /**
         * 错误码。
         */
        private String code;

        /**
         * 错误信息。
         */
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankOutput {

        /**
         * 排序结果。
         */
        private List<RerankItem> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RerankItem {

        /**
         * 原始候选下标。
         */
        private Integer index;

        /**
         * 相关性分数。
         */
        @JsonProperty("relevance_score")
        private Double relevanceScore;
        
    }
}
