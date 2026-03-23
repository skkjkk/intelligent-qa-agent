package com.jujiu.agent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:45
 */
@Data
public class CreateSessionRequest {
    /**
     * 会话标题
     */
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100字符")
    private String title;
}
