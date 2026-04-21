package com.jujiu.agent.module.kb.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库问答历史响应对象。
 *
 * <p>用于返回用户历史知识库问答记录的摘要信息，
 * 支撑历史查询、反馈和后续统计分析等能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "KbQueryHistoryResponse", description = "知识库问答历史响应对象")
public class KbQueryHistoryResponse {
    
    /**
     * 查询日志 ID。。
     */
    @Schema(description = "问答记录ID")
    private Long queryLogId;

    /**
     * 知识库 ID。
     */
    @Schema(description = "知识库ID")
    private Long kbId;
    
    /**
     * 问题。
     */
    @Schema(description = "用户问题")
    private String question;
    
    /**
     * 回答。
     */
    @Schema(description = "模型回答")
    private String answer;

    /**
     * 查询来源。
     */
    @Schema(description = "查询来源")
    private String querySource;

    /**
     * 检索模式。
     */
    @Schema(description = "检索模式")
    private String retrievalMode;

    /**
     * 检索数量。
     */
    @Schema(description = "检索数量")
    private Integer retrievalTopK;

    /**
     * 查询状态。
     */
    @Schema(description = "查询状态")
    private String status;

    /**
     * 总耗时。
     */
    @Schema(description = "总耗时，单位毫秒")
    private Integer latencyMs;

    /**
     * 总 Token 数。
     */
    @Schema(description = "总Token数")
    private Integer totalTokens;
    
    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
