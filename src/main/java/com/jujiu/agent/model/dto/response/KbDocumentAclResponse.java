package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档 ACL 响应对象。用于返回文档访问控制权限的详细信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 15:17
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "知识库文档访问权限响应")
public class KbDocumentAclResponse {
    /** ACL 记录 ID。 */
    @Schema(description = "ACL 记录 ID")
    private Long id;
    /** 文档 ID。 */
    @Schema(description = "文档 ID")
    private Long documentId;
    /** 主体类型，例如 USER、GROUP 等。 */
    @Schema(description = "主体类型，例如 USER、GROUP 等")
    private String principalType;
    /** 主体标识，例如用户 ID。 */
    @Schema(description = "主体标识，例如用户 ID")
    private String principalId;
    /** 权限类型，例如 READ、rebuildFailedIndexes 是做什么的 等。 */
    @Schema(description = "权限类型，例如 READ、rebuildFailedIndexes 是做什么的 等")
    private String permission;
    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
