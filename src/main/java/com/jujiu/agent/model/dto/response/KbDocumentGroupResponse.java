package com.jujiu.agent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 20:06
 */
@Data
@Builder
public class KbDocumentGroupResponse {
    /**
     * id
     */
    private Long id;
    /**
     * 文档id
     */
    private Long documentId;
    /**
     * 用户组id
     */
    private Long groupId;
    /**
     * 用户组名
     */
    private String groupName;
    /**
     * 用户组代码
     */
    private String groupCode;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
