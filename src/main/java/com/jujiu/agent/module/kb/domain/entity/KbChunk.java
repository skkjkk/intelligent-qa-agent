package com.jujiu.agent.module.kb.domain.entity;

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
 * 知识库分片实体类
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 18:45
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_chunk")
@Schema(description = "知识库分片实体", title = "KbChunk")
public class KbChunk {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField(value = "document_id")
    private Long documentId;
    
    @TableField(value = "chunk_index")
    private Integer chunkIndex;
    
    @TableField(value = "content")
    private String content;
    
    @TableField(value = "summary")
    private String summary;
    
    @TableField(value = "char_count")
    private Integer charCount;
    
    @TableField(value = "token_count")
    private Integer tokenCount;
    
    @TableField(value = "keywords")
    private String keywords;
    
    @TableField(value = "section_title")
    private String sectionTitle;
    
    @TableField(value = "enabled")
    private Integer enabled;
    
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
