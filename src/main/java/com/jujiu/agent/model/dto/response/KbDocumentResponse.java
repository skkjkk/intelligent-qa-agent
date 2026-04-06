package com.jujiu.agent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档响应。
 *
 * <p>屏蔽内部字段（如 {@code contentHash}、{@code filePath}），
 * 仅向前端暴露可读的业务字段。
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 10:03
 */
@Data
@Builder
public class KbDocumentResponse implements Serializable {

    /** 文档 ID。 */
    private Long id;
    
    /** 文档标题。 */
    private String title;
    
    /** 原始文件名。 */
    private String fileName;

    /** 文件类型，如 txt、pdf。 */
    private String fileType;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 文档总状态。 */
    private String status;

    /** 解析状态。 */
    private String parseStatus;

    /** 索引状态。 */
    private String indexStatus;

    /** 分块数量。 */
    private Integer chunkCount;

    /** 可见性。 */
    private String visibility;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
