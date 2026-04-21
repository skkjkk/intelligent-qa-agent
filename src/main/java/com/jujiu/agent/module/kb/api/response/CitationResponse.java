package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用信息响应对象。
 *
 * <p>用于向前端返回本次知识库问答所引用的资料来源，
 * 支持答案溯源和结果解释，是最小 RAG 闭环中的关键输出之一。
 *
 * <p>该对象通常由 {@code ChunkSearchResult} 转换而来，
 * 用于对外暴露更适合展示的引用信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "引用信息响应", description = "知识库问答引用信息")
public class CitationResponse {

    /**
     * chunk ID
     */
    @Schema(description = "分块ID", example = "101")
    private Long chunkId;

    /**
     * 文档 ID
     */
    @Schema(description = "文档ID", example = "10")

    private Long documentId;

    /**
     * 文档标题
     */
    @Schema(description = "文档标题", example = "员工手册")
    private String documentTitle;

    /**
     * 引用片段。
     *
     * <p>通常为命中 chunk 的原始内容或截断后的片段，
     * 用于前端展示用户当前答案依赖的证据文本。
     */
    @Schema(description = "引用片段", example = "员工入职满一年后可享受带薪年假。")
    private String snippet;

    /**
     * 检索得分
     */
    @Schema(description = "检索得分", example = "0.9132")
    private Double score;

    /**
     * 排名。
     */
    @Schema(description = "排序名次", example = "1")
    private Integer rank;
}
