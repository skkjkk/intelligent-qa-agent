package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import lombok.Data;

import java.util.Map;

/**
 * DeepSeek provider 工具定义 DTO。
 */
@Data
public class DeepSeekToolDefinition {
    /** 工具类型。 */
    private String type = "function";

    /** 函数定义。 */
    private Function function;

    @Data
    public static class Function {
        /** 工具名称。 */
        private String name;

        /** 工具描述。 */
        private String description;

        /** JSON Schema 参数。 */
        private Map<String, Object> parameters;
    }
}
