package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库问答响应对象。
 *
 * <p>用于返回最小 RAG 闭环的最终结果，包含模型生成答案、
 * 引用信息以及本次请求的基础统计数据。
 *
 * <p>当前阶段该对象主要服务于：
 * <ul>
 *     <li>{@code /api/v1/kb/query} 接口返回</li>
 *     <li>后续流式问答结果结构对齐</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "知识库问答响应", description = "知识库问答响应结果")
public class KnowledgeQueryResponse {

    /**
     * 模型生成的答案。
     */
    @Schema(description = "模型生成的答案", example = "根据知识库资料，员工入职满一年后可享受带薪年假。[1]")
    private String answer;


    /**
     * 引用列表。
     *
     * <p>用于标识本次答案所依赖的文档证据来源。
     */
    @Schema(description = "引用列表")
    private List<CitationResponse> citations;

    /**
     * 输入 token 数。
     *
     * <p>该值通常来自模型调用返回结果，用于记录输入消耗。
     */
    @Schema(description = "输入Token数", example = "520")
    private Integer promptTokens;

    /**
     * 输出 token 数。
     *
     * <p>该值通常来自模型调用返回结果，用于记录输出消耗。
     */
    @Schema(description = "输出Token数", example = "210")
    private Integer completionTokens;

    /**
     * 总 token 数。
     */
    @Schema(description = "总Token数", example = "730")
    private Integer totalTokens;

    /**
     * 请求总耗时，单位毫秒。
     */
    @Schema(description = "总耗时，单位毫秒", example = "1280")
    private Long latencyMs;
}
