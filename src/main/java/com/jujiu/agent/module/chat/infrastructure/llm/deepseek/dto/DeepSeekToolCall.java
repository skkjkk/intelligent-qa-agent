package com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto;

import lombok.Data;

/**
 * DeepSeek provider 工具调用 DTO。
 */
@Data
public class DeepSeekToolCall {
    /** 工具调用 ID。 */
    private String id;

    /** 类型。 */
    private String type;

    /** 函数信息。 */
    private Function function;

    @Data
    public static class Function {
        /** 工具名称。 */
        private String name;

        /** 工具参数 JSON。 */
        private String arguments;
    }
}
