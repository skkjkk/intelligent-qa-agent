package com.jujiu.agent.model.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文档上传请求。
 *
 * <p>与 {@link org.springframework.web.multipart.MultipartFile} 配合使用，
 * 通过 {@code multipart/form-data} 提交。
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 10:00
 */
@Data
public class UploadDocumentRequest implements Serializable {
    
    /** 知识库 ID，不传递时默认使用主知识库。 */
    private Long kbId;
    
    /** 文档标题；若为空，服务端使用原始文件名作为标题。 */
    private String title;
    
    /** 可见性：{@code PRIVATE}、{@code TEAM}、{@code PUBLIC}。 */
    private String visibility;
    
    /** 关联标签 ID 列表，可选。 */
    private List<Long> tagIds;

    /** 关联分组 ID，可选。 */
    private Long groupId;

}
