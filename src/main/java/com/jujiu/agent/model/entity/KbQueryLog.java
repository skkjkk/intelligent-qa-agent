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
 * 知识库查询日志实体类
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 20:31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_query_log")
@Schema(description = "知识库查询日志实体", title = "KbQueryLog")
public class KbQueryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("kb_id")
    private Long kbId;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("session_id")
    private String sessionId;
    
    @TableField("query_source")
    private String querySource;
    
    @TableField("question")
    private String question;

    @TableField("rewritten_question")
    private String rewrittenQuestion;
    
    @TableField("answer")
    private String answer;
    
    @TableField("retrieval_top_k")
    private Integer retrievalTopK;
    
    @TableField("retrieval_mode")
    private String retrievalMode;
    
    @TableField("cited_chunk_ids")
    private String citedChunkIds;
    
    @TableField("prompt_tokens")
    private Integer promptTokens;
    
    @TableField("completion_tokens")
    private Integer completionTokens;
    
    @TableField("total_tokens")
    private Integer totalTokens;
    
    @TableField("latency_ms")
    private Integer latencyMs;
    
    @TableField("status")
    private String status;
    
    @TableField("error_message")
    private String errorMessage;
    
    @TableField("created_at")
    private LocalDateTime createdAt;    

}
