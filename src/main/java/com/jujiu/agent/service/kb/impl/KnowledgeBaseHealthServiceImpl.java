package com.jujiu.agent.service.kb.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.jujiu.agent.config.KnowledgeBaseProperties;
import com.jujiu.agent.model.dto.response.KbHealthResponse;
import com.jujiu.agent.service.kb.KnowledgeBaseHealthService;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

/**
 * 知识库健康检查服务实现。
 *
 * <p>当前版本主要检查 MySQL、Redis、Elasticsearch 的可用性，
 * 并对 MinIO、Kafka 做基础配置状态校验。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
@Service
@Slf4j
public class KnowledgeBaseHealthServiceImpl implements KnowledgeBaseHealthService {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ElasticsearchClient elasticsearchClient;
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    private final MinioClient minioClient;
    private final KafkaAdmin kafkaAdmin;

    public KnowledgeBaseHealthServiceImpl(JdbcTemplate jdbcTemplate, 
                                          StringRedisTemplate stringRedisTemplate, 
                                          ElasticsearchClient elasticsearchClient, 
                                          MinioClient minioClient, 
                                          KnowledgeBaseProperties knowledgeBaseProperties,
                                          KafkaAdmin kafkaAdmin) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.elasticsearchClient = elasticsearchClient;
        this.minioClient = minioClient;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.kafkaAdmin = kafkaAdmin;
    }
    
    /**
     * 执行知识库健康检查。
     *
     * @return 健康检查结果
     */
    @Override
    public KbHealthResponse checkHealth() {
        String mysqlStatus = checkMysql();
        String redisStatus = checkRedis();
        String elasticsearchStatus = checkElasticsearch();
        String minioStatus = checkMinioConfig();
        String kafkaStatus = checkKafkaConfig();
        
        String overallStatus = resolveOverallStatus(
                mysqlStatus,
                redisStatus,
                elasticsearchStatus,
                minioStatus,
                kafkaStatus
        );
        
        String message = buildMessage(
                mysqlStatus,
                redisStatus,
                elasticsearchStatus,
                minioStatus,
                kafkaStatus
        );
        
        log.info("[KB][HEALTH] 健康检查完成 - status={}, mysql={}, redis={}, es={}, minio={}, kafka={}",
                overallStatus, mysqlStatus, redisStatus, elasticsearchStatus, minioStatus, kafkaStatus);

        return KbHealthResponse.builder()
                .status(overallStatus)
                .mysqlStatus(mysqlStatus)
                .redisStatus(redisStatus)
                .elasticsearchStatus(elasticsearchStatus)
                .minioStatus(minioStatus)
                .kafkaStatus(kafkaStatus)
                .message(message)
                .build();
    }

    /**
     * 解析总体状态。
     *
     * @param mysqlStatus MySQL状态
     * @param redisStatus Redis状态
     * @param elasticsearchStatus Elasticsearch状态
     * @param minioStatus MinIO状态
     * @param kafkaStatus Kafka状态
     * @return 总体状态
     */
    private String resolveOverallStatus(String mysqlStatus, 
                                        String redisStatus, 
                                        String elasticsearchStatus,
                                        String minioStatus, 
                                        String kafkaStatus) {
        return "UP".equals(mysqlStatus)
                && "UP".equals(redisStatus)
                && "UP".equals(elasticsearchStatus)
                && "UP".equals(minioStatus)
                && "UP".equals(kafkaStatus)
                ? "UP"
                : "DOWN";
    }
    
    /**
     * 构造说明信息。
     *
     * @param mysqlStatus MySQL状态
     * @param redisStatus Redis状态
     * @param elasticsearchStatus Elasticsearch状态
     * @param minioStatus MinIO状态
     * @param kafkaStatus Kafka状态
     * @return 说明信息
     */
    private String buildMessage(String mysqlStatus,
                                String redisStatus,
                                String elasticsearchStatus,
                                String minioStatus,
                                String kafkaStatus) {
        return "mysql=" + mysqlStatus
                + ", redis=" + redisStatus
                + ", elasticsearch=" + elasticsearchStatus
                + ", minio=" + minioStatus
                + ", kafka=" + kafkaStatus;
    }
    
    /**
     * 检查 Kafka 配置状态。
     *
     * @return 状态字符串
     */
    private String checkKafkaConfig() {
        try {
            if (knowledgeBaseProperties == null || knowledgeBaseProperties.getKafka() == null) {
                return "DOWN";
            }

            if (!hasText(knowledgeBaseProperties.getKafka().getTopicDocumentProcess())) {
                return "DOWN";
            }

            kafkaAdmin.initialize();
            return "UP";
        } catch (Exception e) {
            log.warn("[KB][HEALTH] Kafka 检查失败", e);
            return "DOWN";
        }
    }

    /**
     * 检查 MinIO 配置状态。
     *
     * @return 状态字符串
     */
    private String checkMinioConfig() {
        try {
            if (knowledgeBaseProperties == null || knowledgeBaseProperties.getMinio() == null) {
                return "DOWN";
            }

            String bucketName = knowledgeBaseProperties.getMinio().getBucketName();
            if (!hasText(bucketName)) {
                return "DOWN";
            }

            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            return exists ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("[KB][HEALTH] MinIO 检查失败", e);
            return "DOWN";
        }
    }

    /**
     * 检查 Elasticsearch 状态。
     *
     * @return 状态字符串
     */
    private String checkElasticsearch() {
        try {
            boolean pingResult = elasticsearchClient.ping().value();
            return pingResult ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("[KB][HEALTH] Elasticsearch 检查失败", e);
            return "DOWN";
        }
    }

    /**
     * 检查 Redis 状态。
     *
     * @return 状态字符串
     */
    private String checkRedis() {
        try {
            String pong;
            pong = stringRedisTemplate.getConnectionFactory() != null
                    ? stringRedisTemplate.getConnectionFactory()
                      .getConnection()
                      .ping() : null;
            return pong != null ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("[KB][HEALTH] Redis 检查失败", e);
            return "DOWN";
        }
    }

    /**
     * 检查 MySQL 状态。
     *
     * @return 状态字符串
     */
    private String checkMysql() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("[KB][HEALTH] MySQL 检查失败", e);
            return "DOWN";
        }
    }

    /**
     * 判断字符串是否有内容。
     *
     * @param value 输入值
     * @return true 表示非空白
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
