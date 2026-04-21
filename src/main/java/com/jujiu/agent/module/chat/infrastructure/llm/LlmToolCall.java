package com.jujiu.agent.module.chat.infrastructure.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 18:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmToolCall {
    /**
     * 工具调用ID
     */
    @Schema(description = "工具调用ID")
    private String id;
    /**
     * 工具调用类型
     */
    @Schema(description = "工具调用类型")
    private String type;       // "function"
    /**
     * 工具调用名称
     */
    @Schema(description = "工具调用名称")
    private String name;       // function name
    /**
     * 工具调用参数
     */
    @Schema(description = "工具调用参数")
    private String arguments;  // JSON string
}
