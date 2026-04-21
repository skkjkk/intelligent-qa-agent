package com.jujiu.agent.module.chat.infrastructure.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 统一的 LLM 调用结果。
 * <p>
 * 业务层只依赖该对象，不再直接感知 DeepSeek 等具体提供商返回的 DTO。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:35
 */
@Data
public class LlmResult {
    /** AI 最终回复文本。 */
    @Schema(description = "AI 回复内容", title = "AI Reply")
    private String reply;

    /** 模型返回的工具调用列表。 */
    @Schema(description = "工具调用列表", title = "Tool Calls")
    private List<LlmToolCall> toolCalls;

    /** 提示词 Token 数。 */
    @Schema(description = "提示词Tokens数", title = "Prompt Token 数")
    private int promptTokens;

    /** 生成内容 Token 数。 */
    @Schema(description = "生成内容Tokens数", title = "Completion Token 数")
    private int completionTokens;

    /** 总 Token 数。 */
    @Schema(description = "总Tokens数", title = "Total Token 数")
    private int totalTokens;

    /** 完成原因，例如 stop、tool_calls。 */
    @Schema(description = "完成原因", title = "Finish Reason")
    private String finishReason;
}
