package com.jujiu.agent.model.dto.deepseek;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具定义DTO - 用于DeepSeek API的tools参数
 * @author 17644
 */
@Data
public class ToolDefinition {
    @NotBlank(message = "类型不能为空")
    private String type = "function";
    
    @NotBlank(message = "函数信息不能为空")
    private Function function;

    @Data
    public static class Function {
        // 工具名称
        @NotBlank(message = "工具名称不能为空")
        private String name;

        // 工具描述
        @NotBlank(message = "工具描述不能为空")
        private String description;
        
        // 参数定义
        @NotBlank(message = "参数定义不能为空")
        private Map<String, Object> parameters;
    }

    @Data
    public static class Parameters {
        // 参数类型
        @NotBlank(message = "参数类型不能为空")
        private String type = "object";
        // 参数属性
        @NotBlank(message = "参数属性不能为空")
        private Map<String, Property> properties;
        // 必填参数
        @NotBlank(message = "必填参数不能为空")
        private List<String> required;
    }

    @Data
    public static class Property {
        // 属性类型
        @NotBlank(message = "属性类型不能为空")
        private String type;
        // 属性描述
        @NotBlank(message = "属性描述不能为空")
        private String description;
    }
}
