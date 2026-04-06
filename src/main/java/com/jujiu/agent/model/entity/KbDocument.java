package com.jujiu.agent.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体类
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 18:05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_document")
@Schema(description = "知识库文档实体", title = "KbDocument")
public class KbDocument {

    /**
     * 文档 ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库 ID
     */
    @TableField("kb_id")
    private Long kbId;

    /**
     * 文档标题
     */
    @TableField("title")
    private String title;

    /**
     * 文件名称
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件类型
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件存储路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 内容哈希值（用于去重和完整性校验）
     */
    @TableField("content_hash")
    private String contentHash;

    /**
     * 文档状态
     */
    @TableField("status")
    private String status;

    /**
     * 解析状态
     */
    @TableField("parse_status")
    private String parseStatus;
    
    /**
     * 索引状态
     */
    @TableField("index_status")
    private String indexStatus;
    
    /**
     * 文本块数量
     */
    @TableField("chunk_count")
    private Integer chunkCount;
    
    /**
     * 所属用户 ID
     */
    @TableField("owner_user_id")
    private Long ownerUserId;
    
    /**
     * 可见性（公开/私有）
     */
    @TableField("visibility")
    private String visibility;
    
    /**
     * 是否启用（1-启用，0-禁用）
     */
    @TableField("enabled")
    private Integer enabled;
    
    /**
     * 是否删除（1-已删除，0-未删除）
     */
    @TableField("deleted")
    private Integer deleted;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
