package com.jujiu.agent.model.dto.deepseek;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具定义DTO - 用于DeepSeek API的tools参数
 * @author 17644
 */
@Data
public class ToolDefinition {
    private String type = "function";
    private Function function;

    @Data
    public static class Function {
        // 工具名称
        private String name;
        // 工具描述
        private String description;
        // 参数定义
        private Parameters parameters;
    }

    @Data
    public static class Parameters {
        // 参数类型
        private String type = "object";
        // 参数属性
        private Map<String, Property> properties;
        // 必填参数
        private List<String> required;
    }

    @Data
    public static class Property {
        // 属性类型
        private String type;
        // 属性描述
        private String description;
    }
}
