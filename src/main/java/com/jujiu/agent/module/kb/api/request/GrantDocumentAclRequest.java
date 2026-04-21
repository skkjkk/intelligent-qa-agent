package com.jujiu.agent.module.kb.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 15:19
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "授权文档访问权限请求")
public class GrantDocumentAclRequest {
    @NotBlank(message = "principalType 不能为空")
    private String principalType;

    @NotBlank(message = "principalId 不能为空")
    private String principalId;

    @NotBlank(message = "permission 不能为空")
    private String permission;
}
