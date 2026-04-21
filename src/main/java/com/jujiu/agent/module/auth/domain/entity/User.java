package com.jujiu.agent.module.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * 对应数据库 user 表，存储系统用户的基本信息
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 14:35
 */
@Data
@TableName("user")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户信息", title = "系统用户")
public class User {
    /**
     * 用户 ID（主键）
     */
    @Schema(description = "用户 ID", title = "用户唯一标识", example = "1")
    private Long id;

    /**
     * 用户名（登录账号）
     */
    @Schema(description = "用户名", title = "登录账号", example = "admin")
    private String username;
    
    /**
     * 密码（BCrypt 加密存储）
     */
    @Schema(description = "密码", title = "登录密码（加密存储）", example = "$2a$10$N9qo8uLOickgx2ZMRZoMye...")
    private String password;
    
    /**
     * 邮箱地址
     */
    @Schema(description = "邮箱", title = "用户邮箱", example = "user@example.com")
    private String email;
    
    /**
     * 用户昵称
     */
    @Schema(description = "昵称", title = "显示名称", example = "张三")
    private String nickname;
    
    /**
     * 用户角色（USER/ADMIN）
     */
    @Schema(description = "角色", title = "用户角色：USER-普通用户，ADMIN-管理员", example = "USER")
    private String role;
    
    /**
     * 用户状态（1-正常，0-禁用）
     */
    @Schema(description = "状态", title = "用户状态：1-正常，0-禁用", example = "1")
    private Integer status;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间", title = "账号创建时间", example = "2026-03-22 10:00:00")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间", title = "最后修改时间", example = "2026-03-22 15:30:00")
    private LocalDateTime updatedAt;
}
