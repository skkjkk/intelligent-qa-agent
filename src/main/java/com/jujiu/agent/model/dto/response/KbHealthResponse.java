package com.jujiu.agent.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库健康检查响应对象。
 *
 * <p>用于返回知识库相关依赖的健康状态，包括数据库、缓存、
 * 搜索引擎和必要配置项等信息。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库健康检查响应对象")
public class KbHealthResponse {
    /**
     * 总体状态。
     */
    @Schema(description = "总体状态", example = "UP")
    private String status;
    
    /**
     * MySQL 状态。
     */
    @Schema(description = "MySQL状态", example = "UP")
    private String mysqlStatus;

    /**
     * Redis 状态。
     */
    @Schema(description = "Redis状态", example = "UP")
    private String redisStatus;

    /**
     * Elasticsearch 状态。
     */
    @Schema(description = "Elasticsearch状态", example = "UP")
    private String elasticsearchStatus;

    /**
     * MinIO 配置状态。
     */
    @Schema(description = "MinIO配置状态", example = "UP")
    private String minioStatus;

    /**
     * Kafka 配置状态。
     */
    @Schema(description = "Kafka配置状态", example = "UP")
    private String kafkaStatus;

    /**
     * 说明信息。
     */
    @Schema(description = "说明信息")
    private String message;

    /**
     * 组件明细列表。
     */
    @Schema(description = "组件明细列表")
    private java.util.List<KbHealthComponentDetail> details;

}
