package com.jujiu.agent.module.chat.infrastructure.deepseek;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek API 响应 DTO
 * 
 * 封装从 DeepSeek API 返回的响应数据
 * 包含 AI 生成的回复消息和完成状态
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 10:06
 */
@Data
@Schema(description = "DeepSeek 响应", title = "DeepSeek API 返回结果")
public class DeepSeekResponse {
    
    /**
     * 选择列表
     * 包含 AI 生成的所有可能回复（通常只有一个）
     */
    @Schema(description = "选择列表", title = "AI 回复选项")
    private List<Choice> choices;
    
    /**
     * 使用情况
     * 包含提示词令牌数和总令牌数
     */
    @Schema(description = "使用情况", title = "Token 使用统计")
    private Usage usage;
    
    /**
     * 选择结果内部类
     * 封装单个 AI 回复的详细信息
     */
    @Data
    @Schema(description = "选择结果", title = "单个 AI 回复详情")
    public static class Choice{
        
        /**
         * AI 回复的消息内容
         * 包含角色标识和具体文本
         */
        @Schema(description = "消息内容", title = "AI 回复的消息")
        private DeepSeekMessage message;
        
        /**
         * 完成原因
         * 标识生成结束的原因（如：stop, length 等）
         * 示例："stop", "length"
         */
        @Schema(description = "完成原因", title = "生成结束原因", example = "stop")
        @JsonProperty("finish_reason")
        private String finishReason;
    }
    
    @Data
    public static class Usage{
        /**
         * 使用情况
         * 包含提示词令牌数和总令牌数
         */
        @Schema(description = "使用情况", title = "Token 使用统计")
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        /**
         * 提示词令牌数
         * 包含生成的提示词的令牌数
         */

        @JsonProperty("completion_tokens")
        @Schema(description = "提示词令牌数", title = "Prompt Token 数")
        private int completionTokens;
        /**
         * 总令牌数
         * 包含提示词和生成内容的令牌数
         */
        @JsonProperty("total_tokens")
        @Schema(description = "总令牌数", title = "Total Token 数")
        private int totalTokens;
        
    }
}
