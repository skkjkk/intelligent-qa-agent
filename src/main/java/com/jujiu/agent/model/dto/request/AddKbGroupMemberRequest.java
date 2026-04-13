package com.jujiu.agent.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:44
 */
@Data
public class AddKbGroupMemberRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    private String role = "MEMBER";
}