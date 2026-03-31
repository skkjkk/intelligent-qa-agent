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
 * 工具实体类（对应数据库tool表）
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 13:51
 */
@Data
@Schema(description = "工具实体类（对应数据库tool表）", title = "工具实体类")
@TableName("tool")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tool {
    /**
     * 工具ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 工具唯一标识（如 "weather"）
     */
    @TableField("tool_name")
    private String toolName;
    /**
     * 显示名称（如 "天气查询"）
     */
    @TableField("display_name")
    private String displayName;
    /**
     * 工具描述（AI会根据这个决定何时调用）
     */
    @TableField("description")
    private String description;
    
    /**
     * 参数定义（JSON格式）
     * 例如：{"city": {"type": "string", "required": true, "description": "城市名称"}}
     */
    private String parameters;

    /**
     * 实现类名（用于动态调用）
     */
    @TableField("class_name")
    private String className;
    
    /**
     * 状态（0:禁用 1:启用）
     */
    private Integer status;
    
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
