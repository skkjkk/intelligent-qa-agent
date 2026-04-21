package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DeepSeek 请求 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekRequest {
    /** 模型。 */
    private String model;

    /** 消息列表。 */
    private List<DeepSeekProviderMessage> messages;

    /** 是否流式。 */
    private Boolean stream;

    /** 温度。 */
    private Double temperature;

    /** 工具列表。 */
    private List<DeepSeekToolDefinition> tools;
}
