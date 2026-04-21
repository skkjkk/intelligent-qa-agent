package com.jujiu.agent.module.auth.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录响应 DTO
 * @author 17644
 */
@Schema(description = "用户登录响应", title = "登录成功后的令牌信息")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
   
    @Schema(description = "访问令牌 (JWT)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    // JWT Token
    private String accessToken;
    
    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    // 刷新 Token
    private String refreshToken;
    
    @Schema(description = "令牌类型", example = "Bearer")
    // Token 类型，固定 "Bearer"
    private String tokenType;
    
    @Schema(description = "过期时间（秒）", example = "86400")
    // 过期时间（秒）
    private Long expiresIn;
}
