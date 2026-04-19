package com.jujiu.agent.service.kb.model;

import com.jujiu.agent.common.result.ChunkSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索调试结果对象。
 *
 * <p>用于承接检索层中间态结果，便于调试：
 * <ul>
 *     <li>向量候选</li>
 *     <li>BM25 候选</li>
 *     <li>融合结果</li>
 *     <li>平衡后结果</li>
 *     <li>rerank 后结果</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/4/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "检索调试结果", description = "检索层中间态调试结果")
public class SearchDebugResult {

    @Schema(description = "向量候选结果")
    private List<ChunkSearchResult> vectorCandidates;

    @Schema(description = "BM25 候选结果")
    private List<ChunkSearchResult> bm25Candidates;

    @Schema(description = "融合结果")
    private List<ChunkSearchResult> mergedCandidates;

    @Schema(description = "轻量平衡后的结果")
    private List<ChunkSearchResult> balancedCandidates;

    @Schema(description = "rerank 后结果")
    private List<ChunkSearchResult> rerankedCandidates;
}
