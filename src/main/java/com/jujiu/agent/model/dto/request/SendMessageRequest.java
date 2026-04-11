package com.jujiu.agent.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:46
 */
@Data
@Schema(description = "发送消息请求参数", title = "发送消息请求参数")
public class SendMessageRequest {

    @NotBlank(message = "会话ID不能为空")
    @Schema(description = "会话ID")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息长度不能超过4000字符")
    @Schema(description = "消息内容")
    private String message;
    
    /**
     * 是否启用知识库增强。
     */
    @Schema(description = "是否启用知识库增强")
    private Boolean enableKnowledgeBase = false;

    /**
     * 知识库 ID。
     */
    @Schema(description = "知识库 ID")
    private Long knowledgeBaseId;

    /**
     * 检索返回数量。
     */
    @Schema(description = "检索返回数量")
    private Integer retrievalTopK = 5;
}
