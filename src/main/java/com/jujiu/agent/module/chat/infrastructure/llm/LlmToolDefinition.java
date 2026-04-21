package com.jujiu.agent.module.chat.infrastructure.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:37
 */
@Data
public class LlmToolDefinition {
    @NotBlank(message = "类型不能为空")
    @Schema(description = "工具类型", title = "工具类型", example = "function")
    private String type = "function";
    
    @NotBlank(message = "函数不能为空")
    @Schema(description = "函数定义", title = "函数定义")
    private Function function;

    /**
     * 函数定义
     */
    @Data
    public static class Function {
        // 工具名称
        @NotBlank(message = "工具名称不能为空")
        @Schema(description = "工具名称", title = "工具名称", example = "get_weather")
        private String name;

        // 工具描述
        @NotBlank(message = "工具描述不能为空")
        @Schema(description = "工具描述", title = "工具描述", example = "获取天气信息")
        private String description;

        // 参数定义
        @NotBlank(message = "参数定义不能为空")
        @Schema(description = "参数定义", title = "参数定义")
        private Map<String, Object> parameters;
    }
}
