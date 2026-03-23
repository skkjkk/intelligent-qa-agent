package com.jujiu.agent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改密码请求 DTO
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 9:14
 */
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{8,16}$", 
            message = "新密码必须是8-16位字母和数字的组合")
    private String newPassword;
    
    
    
}
