package com.jujiu.agent.module.tool.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 15:42
 */
@Data
@Schema(description = "工具响应参数", title = "工具响应参数")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolResponse {
    
    // 工具ID
    @Schema(description = "工具ID", title = "工具ID")
    private String toolId;
    
    // 工具名称
    @Schema(description = "工具名称（如 \"weather\"）", title = "工具名称")
    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    // 显示名称
    @Schema(description = "显示名称（如 \"天气查询\"）", title = "显示名称")
    @NotBlank(message = "显示名称不能为空")
    private String displayName;
    
    // 描述
    @Schema(description = "工具描述", title = "工具描述")
    @NotBlank(message = "工具描述不能为空")
    private String description;
    
    // 参数
    @Schema(description = "参数定义列表", title = "参数定义列表")
    private List<ParameterDefinition> parameters;

    /**
     * 参数定义内部类
     */
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "参数定义", title = "参数定义")
    public static class ParameterDefinition{
        // 参数名称
        @Schema(description = "参数名称", title = "参数名称")
        @NotBlank(message = "参数名称不能为空")
        private String name;
        // 参数类型
        @Schema(description = "参数类型", title = "参数类型")
        @NotBlank(message = "参数类型不能为空")
        private String type;
        // 是否必填
        @Schema(description = "是否必填", title = "是否必填")
        @NotNull(message = "必填标志不能为空")
        private boolean required;
        // 描述
        @Schema(description = "参数描述", title = "参数描述")
        private String description;
    }
}
