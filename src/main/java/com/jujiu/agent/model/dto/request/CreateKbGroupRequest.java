package com.jujiu.agent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:43
 */
@Data
public class CreateKbGroupRequest {
    @NotBlank(message = "name 不能为空")
    private String name;

    @NotBlank(message = "code 不能为空")
    private String code;
}
