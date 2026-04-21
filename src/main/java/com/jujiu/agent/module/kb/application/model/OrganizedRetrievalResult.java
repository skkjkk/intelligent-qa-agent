package com.jujiu.agent.module.kb.application.model;

import com.jujiu.agent.module.kb.api.response.CitationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索结果整理后的统一输出对象。
 *
 * <p>该对象用于承接“原始检索结果 -> 结果整理层”之后的最终结构化结果，
 * 统一服务于以下几条链路：
 * <ul>
 *     <li>知识库同步问答 {@code /api/v1/kb/query}</li>
 *     <li>知识库流式问答 {@code /api/v1/kb/query/stream}</li>
 *     <li>聊天显式知识增强 {@code ChatServiceImpl}</li>
 *     <li>工具调用 {@code KnowledgeBaseTool}</li>
 * </ul>
 *
 * <p>它的作用不是替代原始检索结果，而是把最终可用于：
 * <ul>
 *     <li>Prompt 上下文组装</li>
 *     <li>引用列表生成</li>
 *     <li>空结果原因解释</li>
 *     <li>后续 trace / 诊断能力扩展</li>
 * </ul>
 * 所需的信息统一收口。
 *
 * <p>这一层稳定后，后续若接入 rerank、query rewrite、空结果分析等能力，
 * 也应当继续落在该对象语义下扩展，而不是重新打散到 RagService 中。
 *
 * @author 17644
 * @since 2026/4/17
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "整理后的检索结果", description = "用于统一承接检索结果整理后的上下文、引用与空结果原因")
public class OrganizedRetrievalResult {
    /**
     * 最终保留下来的检索候选。
     *
     * <p>注意：这里仍然沿用现有 {@link ChunkSearchResult}，
     * 是为了第一阶段尽量减少对项目已有结构的冲击。
     * 后续如果你决定引入更丰富的候选元数据，
     * 再单独演进为 RetrievalCandidate 也不迟。
     */
    @Schema(description = "整理后的最终候选结果")
    private List<ChunkSearchResult> finalResults;

    /**
     * 最终生成的引用列表。
     *
     * <p>该列表应与 finalResults 保持一一对应语义，
     * 避免“给模型的上下文”和“给前端展示的 citation”来自两套不同逻辑。
     */
    @Schema(description = "整理后的引用列表")
    private List<CitationResponse> citations;

    /**
     * 最终用于 Prompt 注入的上下文文本。
     *
     * <p>该字段直接服务于 RAG 生成阶段和显式知识增强阶段。
     */
    @Schema(description = "最终上下文文本")
    private String context;

    /**
     * 原始检索结果数量。
     *
     * <p>用于日志、诊断和后续 trace 扩展。
     */
    @Schema(description = "原始检索结果数量", example = "10")
    private Integer rawResultCount;

    /**
     * 整理后保留结果数量。
     */
    @Schema(description = "整理后保留结果数量", example = "4")
    private Integer finalResultCount;

    /**
     * 空结果原因。
     *
     * <p>第一阶段先使用轻量枚举值：
     * <ul>
     *     <li>NONE：有结果</li>
     *     <li>NO_RAW_RESULTS：原始检索就为空</li>
     *     <li>ALL_FILTERED_AFTER_ORGANIZE：整理后全部被裁掉</li>
     * </ul>
     *
     * <p>后续可再扩展为更细粒度分类：
     * NO_ACCESSIBLE_DOCUMENT / NO_RETRIEVAL_HIT / ACL_FILTERED / LOW_SCORE_FILTERED 等。
     */
    @Schema(description = "空结果原因", example = "NONE")
    private String emptyReason;
}
