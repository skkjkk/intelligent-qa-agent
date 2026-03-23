package com.jujiu.agent.client;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DeepSeek API 调用结果
 * 
 * 封装 AI 回复内容和消耗的令牌数
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 13:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeepSeekResult {
    
    /**
     * AI 回复内容
     * 包含角色标识和具体文本
     */
    @Schema(description = "AI 回复内容", title = "AI Reply")
    private String reply;
    
    /**
     * 总令牌数
     * 包含提示词和生成内容的令牌数
     */
    @Schema(description = "总令牌数", title = "Total Token 数")
    private int totalTokens;

    /**
     * 提示词令牌数
     */
    @Schema(description = "提示词令牌数", title = "Prompt Token 数")
    private int promptTokens;
    
    /**
     * 生成内容令牌数
     */
    @Schema(description = "生成内容令牌数", title = "Completion Token 数")
    private int completionTokens;
}
