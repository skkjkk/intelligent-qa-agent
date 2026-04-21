package com.jujiu.agent.module.kb.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 知识库问答反馈请求对象。
 *
 * <p>用于接收用户对知识库问答结果的反馈信息，
 * 包括是否有帮助、评分以及补充反馈内容。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Schema(description = "知识库问答反馈请求")
public class QueryFeedbackRequest {
    /**
     * 是否有帮助。
     */
    @Schema(description = "是否有帮助", example = "true")
    private Boolean helpful;

    /**
     * 评分，范围 1~5。
     */
    @Min(value = 1, message = "评分不能小于1")
    @Max(value = 5, message = "评分不能大于5")
    @Schema(description = "评分，范围1~5", example = "5")
    private Integer rating;

    /**
     * 反馈内容。
     */
    @Schema(description = "反馈内容", example = "回答很准确，但希望引用更完整")
    private String feedbackContent;
}
