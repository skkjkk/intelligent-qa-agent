package com.jujiu.agent.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户登录日志实体
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 19:02
 */

@Schema(description = "用户登录日志", title = "登录记录信息")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginLog {
    
    @Schema(description = "主键 ID", example = "1")
    private Long id;
    
    @Schema(description = "用户 ID", example = "123")
    private Long userId;
    
    @Schema(description = "登录 IP 地址", example = "192.168.1.100")
    private String ip;
    
    @Schema(description = "用户代理 (浏览器/设备信息)", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    private String userAgent;
    
    @Schema(description = "登录状态：0-失败，1-成功", example = "1")
    private Integer status;
    
    @Schema(description = "登录时间", example = "2026-03-21 19:02:30")
    private LocalDateTime loginTime;
}
