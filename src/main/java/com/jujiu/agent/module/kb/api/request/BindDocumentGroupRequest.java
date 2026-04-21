package com.jujiu.agent.module.kb.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:07
 */
@Data
public class BindDocumentGroupRequest {
    @NotNull(message = "groupId 不能为空")
    private Long groupId;
}
