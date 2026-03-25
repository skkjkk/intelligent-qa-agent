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
    
    /**
     * 是否流式返回
     * 是否边生成边返回
     * 默认：false
     */
    @Schema(description = "是否流式返回", title = "边生成边返回", example = "false")
    private Boolean stream;

    /**
     * 温度参数，控制回复的随机性
     * 范围：0.0 - 2.0，默认 1.0
     * 越低越确定，越高越创意
     */
    @Schema(description = "温度参数", title = "控制回复随机性", example = "0.7")
    private Double temperature;
    
    /**
     * 工具列表
     * 可供AI调用的工具定义列表
     * 当提供此字段时，AI会根据用户意图自动选择合适的工具调用
     */
    @Schema(description = "工具列表", title = "可调用的工具定义")
    private List<ToolDefinition> tools;

}
