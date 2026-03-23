package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 21:09
 */
@Schema(description = "用户信息响应", title = "用户详细信息")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoResponse {
    
    @Schema(description = "用户 ID", example = "123")
    private Long userId;
    
    @Schema(description = "用户名", example = "admin")
    private String username;
    
    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;
    
    @Schema(description = "昵称", example = "管理员")
    private String nickname;
    
    @Schema(description = "角色", example = "ADMIN")
    private String role;
    
    @Schema(description = "创建时间", example = "2026-03-21 21:09:00")
    private LocalDateTime createdAt;
}
