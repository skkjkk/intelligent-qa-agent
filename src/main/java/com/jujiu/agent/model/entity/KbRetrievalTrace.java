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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检索轨迹实体类
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:41
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_retrieval_trace")
@Schema(description = "检索轨迹实体", title = "KbRetrievalTrace")
public class KbRetrievalTrace {
    
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("query_log_id")
    private Long queryLogId;

    @TableField("chunk_id")
    private Long chunkId;

    @TableField("score")
    private BigDecimal score;

    @TableField("rank_no")
    private Integer rankNo;

    @TableField("retrieval_type")
    private String retrievalType;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
