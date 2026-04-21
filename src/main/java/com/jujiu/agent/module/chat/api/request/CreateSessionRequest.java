package com.jujiu.agent.module.chat.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:45
 */
@Data
@Schema(description = "创建会话请求参数", title = "创建会话请求参数")
public class CreateSessionRequest {
    /**
     * 会话标题
     */
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100字符")
    private String title;
}
