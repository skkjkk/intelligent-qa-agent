package com.jujiu.agent.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块检索结果。
 *
 * <p>用于承接向量检索或混合检索返回的单条命中记录，
 * 是 {@code VectorSearchService} 输出给 {@code RagService} 的中间结果对象。
 *
 * <p>当前对象只保留最小 RAG 闭环所需字段：
 * 分块标识、所属文档、标题、片段内容、分数和排序位次。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "文档分块检索结果", description = "向量检索或混合检索返回的单条命中记录")
public class ChunkSearchResult {
    /**
     * 分块 ID。
     */
    @Schema(description = "分块ID", example = "101")
    private Long chunkId;

    /**
     * 所属文档 ID。
     */
    @Schema(description = "所属文档ID", example = "10")
    private Long documentId;

    /**
     * 文档标题。
     */
    @Schema(description = "文档标题", example = "员工手册")
    private String documentTitle;
    
    /**
     * 命中的分块内容。
     *
     * <p>该字段用于后续构造 RAG 上下文和引用信息。
     */
    @Schema(description = "命中的分块内容", example = "员工入职满一年后可享受带薪年假。")
    private String content;

    /**
     * 检索得分。
     *
     * <p>不同检索实现的得分含义可能存在差异，
     * 但在首版中统一作为排序参考和引用展示信息使用。
     */
    @Schema(description = "检索得分", example = "0.9132")
    private Double score;

    /**
     * 排名。
     *
     * <p>表示当前结果在本次检索结果中的排序位置，首位通常为 1。
     */
    @Schema(description = "排序名次", example = "1")
    private Integer rank;
}
