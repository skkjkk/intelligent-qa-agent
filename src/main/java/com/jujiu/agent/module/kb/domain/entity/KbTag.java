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
 * 知识库标签实体类
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_tag")
@Schema(description = "知识库标签实体", title = "KbTag")
public class KbTag {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField(value = "kb_id")
    private Long kbId;
    
    @TableField(value = "name")
    private String name;
    
    @TableField(value = "color")
    private String color;
    
    @TableField(value = "created_by")
    private Long createdBy;
    
    @TableField(value = "created_at")
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
