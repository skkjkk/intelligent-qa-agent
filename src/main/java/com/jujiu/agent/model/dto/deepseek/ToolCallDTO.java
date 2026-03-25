package com.jujiu.agent.model.dto.deepseek;

import lombok.Data;

/**
 * 工具调用记录DTO - 解析DeepSeek API返回的tool_calls
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/25 16:10
 */
@Data
public class ToolCallDTO {
    /** 工具调用的唯一标识 */
    private String id;

    /** 类型，固定为"function" */
    private String type;

    /** 函数信息 */
    private Function function;

    /**
     * 函数信息内部类
     */
    @Data
    public static class Function {
        /** 工具名称，比如"weather" */
        private String name;

        /** 工具参数，JSON格式的字符串，形如{"city": "北京"} */
        private String arguments;
    }
}
