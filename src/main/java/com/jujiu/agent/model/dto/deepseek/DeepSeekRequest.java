package com.jujiu.agent.model.dto.deepseek;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSeek API 请求 DTO
 * 
 * 封装发送给 DeepSeek API 的请求参数
 * 包含模型名称和对话消息列表
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 10:06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "DeepSeek 请求", title = "DeepSeek API 请求参数")
public class DeepSeekRequest {
    
    /**
     * 模型名称
     * 指定要使用的 DeepSeek 模型
     * 示例：deepseek-chat
     */
    @Schema(description = "模型名称", title = "使用的 AI 模型", example = "deepseek-chat")
    private String model;
    
    /**
     * 对话消息列表
     * 包含用户和助手的历史对话记录
     * 用于保持多轮对话的上下文
     */
    @Schema(description = "对话消息列表", title = "多轮对话历史")
    private List<DeepSeekMessage> messages;
}
