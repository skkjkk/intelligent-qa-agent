package com.jujiu.agent.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 执行工具请求DTO
 * 
 * @author 居九
 * @version 1.0.0
 * @since 2026/3/24 15:39
 */
@Data
@Schema(description = "执行工具请求参数", title = "执行工具请求参数")
public class ExecuteToolRequest {

    /**
     * 工具名称（如 "天气查询"）
     */
    @NotBlank(message = "工具名称不能为空")
    @Schema(description = "工具名称（系统标识）", example = "weather")
    private String toolName;

    /**
     * 工具参数（如 {"city": "北京"}）
     * 使用 Map 接收，灵活支持不同工具的不同参数
     * 
     * 【为什么用 @NotNull 而不是 @NotBlank？】
     * - @NotBlank 用于 String 类型，检查是否为空字符串
     * - @NotNull 用于所有引用类型（包括 Map），检查是否为 null
     * - Map 不可能是空字符串，所以必须用 @NotNull
     */
    @NotNull(message = "工具参数不能为空")
    @Schema(description = "工具参数", example = "{\"city\": \"北京\"}")
    private Map<String, Object> parameters;

    // 可选：添加验证方法
    public boolean hasValidParameters() {
        return parameters != null && !parameters.isEmpty();
    }
}
