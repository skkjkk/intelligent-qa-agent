package com.jujiu.agent.module.tool.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 15:46
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "执行工具响应参数", title = "执行工具响应参数")
public class ExecuteToolResponse {

    /**
     * 工具名称
     */

    @NotBlank(message = "工具名称不能为空")
    @Schema(description = "工具名称", title = "工具名称")
    private String toolName;

    /**
     * 执行结果
     */
    @Schema(description = "执行结果", title = "执行结果")
    private String result;

    /**
     * 执行耗时（毫秒）
     */
    @Schema(description = "执行耗时（毫秒）", title = "执行耗时（毫秒）")
    private Long executionTime;

    /**
     * 是否成功
     */
    @NotBlank(message = "是否成功不能为空")
    @Schema(description = "是否成功", title = "是否成功")
    private boolean success;

    /**
     * 错误信息（如果失败）
     */
    @Schema(description = "错误信息（如果失败）", title = "错误信息（如果失败）")
    private String errorMessage;
}
