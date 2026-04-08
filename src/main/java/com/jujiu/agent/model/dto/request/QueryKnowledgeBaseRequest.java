package com.jujiu.agent.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库问答请求参数。
 *
 * <p>用于接收用户发起的知识库检索问答请求，包含问题内容、
 * 知识库范围和检索返回数量等最小必要参数。
 *
 * <p>当前阶段定位为最小 RAG 闭环请求对象，仅服务于：
 * <ul>
 *     <li>独立知识库问答接口</li>
 *     <li>后续 {@code RagService} 的统一输入</li>
 * </ul>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/6
 */
@Data
@Schema(name = "知识库问答请求参数", description = "用于接收用户发起的知识库检索问答请求")
public class QueryKnowledgeBaseRequest {

    /**
     * 用户问题。
     *
     * <p>该字段是知识库问答的核心输入，不能为空，最大长度限制为 2000 个字符，
     * 防止异常超长输入影响检索与模型调用。
     */
    @Schema(description = "用户问题", example = "公司的年假制度是怎样的？", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过2000字符")
    private String question;

    /**
     * 知识库 ID。
     *
     * <p>首版项目默认支持主知识库，可为空；为空时由业务层统一兜底为 {@code 1L}。
     */
    @Schema(description = "知识库 ID，首版可为空，默认使用主知识库", example = "1")
    private Long kbId = 1L;

    /**
     * 检索返回数量。
     *
     * <p>用于控制召回的候选分块数量。首版建议默认值为 5，
     * 并限制在合理范围内，避免一次检索过多内容导致上下文膨胀。
     */
    @Schema(description = "检索返回数量，默认5", example = "5", defaultValue = "5")
    @Min(value = 1, message = "topK不能小于1")
    @Max(value = 10, message = "topK不能大于10")
    private Integer topK = 5;
}
