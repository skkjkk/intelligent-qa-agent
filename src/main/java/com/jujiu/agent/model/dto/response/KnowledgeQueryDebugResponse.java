package com.jujiu.agent.model.dto.response;

import com.jujiu.agent.common.result.ChunkSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库问答调试响应对象。
 *
 * <p>该对象用于调试 RAG 检索主链路的中间态结果，不负责返回最终模型回答。
 *
 * @author 17644
 * @since 2026/4/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "知识库问答调试响应", description = "用于查看 RAG 检索链路中间态结果")
public class KnowledgeQueryDebugResponse {

    @Schema(description = "向量候选结果")
    private List<ChunkSearchResult> vectorCandidates;

    @Schema(description = "BM25 候选结果")
    private List<ChunkSearchResult> bm25Candidates;

    @Schema(description = "融合后的候选结果")
    private List<ChunkSearchResult> mergedCandidates;

    @Schema(description = "轻量文档平衡后的候选结果")
    private List<ChunkSearchResult> balancedCandidates;

    @Schema(description = "rerank 后候选结果")
    private List<ChunkSearchResult> rerankedCandidates;

    @Schema(description = "organizer 最终结果")
    private List<ChunkSearchResult> finalResults;

    @Schema(description = "最终 citation 列表")
    private List<CitationResponse> citations;

    @Schema(description = "最终上下文")
    private String context;

    @Schema(description = "空结果原因", example = "NONE")
    private String emptyReason;
}
