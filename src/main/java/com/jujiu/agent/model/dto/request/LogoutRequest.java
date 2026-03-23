package com.jujiu.agent.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户退出登录请求 DTO
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 20:52
 */
@Schema(description = "用户退出请求", title = "退出登录信息")
@Data
public class LogoutRequest {
    
    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "令牌不能为空")
    private String token;
}
