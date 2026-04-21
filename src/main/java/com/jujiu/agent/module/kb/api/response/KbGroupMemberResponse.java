package com.jujiu.agent.module.kb.api.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:46
 */
@Data
@Builder
public class KbGroupMemberResponse {
    /**
     * id
     */
    private Long id;
    /**
     * group id
     */
    private Long groupId;
    /**
     * user id
     */
    private Long userId;
    /**
     * role
     */
    private String role;
    /**
     * created at
     */
    private LocalDateTime createdAt;
}
