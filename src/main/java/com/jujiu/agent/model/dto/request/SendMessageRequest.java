package com.jujiu.agent.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 10:46
 */
@Data
@Schema(description = "发送消息请求参数", title = "发送消息请求参数")
public class SendMessageRequest {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息长度不能超过4000字符")
    private String message;
}
