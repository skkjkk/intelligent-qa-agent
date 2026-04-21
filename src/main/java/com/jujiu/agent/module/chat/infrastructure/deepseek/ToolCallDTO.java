package com.jujiu.agent.module.chat.infrastructure.deepseek;

import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "工具调用ID不能为空")
    private String id;

    /** 类型，固定为"function" */
    @NotBlank(message = "类型不能为空")
    private String type;

    /** 函数信息 */
    @NotBlank(message = "函数信息不能为空")
    private Function function;

    /**
     * 函数信息内部类
     */
    @Data
    public static class Function {
        /** 工具名称，比如"weather" */
        @NotBlank(message = "工具名称不能为空")
        private String name;

        /** 工具参数，JSON格式的字符串，形如{"city": "北京"} */
        @NotBlank(message = "工具参数不能为空")
        private String arguments;
    }
}
