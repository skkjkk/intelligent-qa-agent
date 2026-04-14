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
    /** JDBC 模板，用于 MySQL 健康检查。 */
    private final JdbcTemplate jdbcTemplate;
    /** Redis 字符串模板，用于 Redis 健康检查。 */
    private final StringRedisTemplate stringRedisTemplate;
    /** Elasticsearch 客户端。 */
    private final ElasticsearchClient elasticsearchClient;
    /** 知识库配置属性。 */
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    /** MinIO 客户端。 */
    private final MinioClient minioClient;
    /** Kafka 管理器。 */
    private final KafkaAdmin kafkaAdmin;

    /**
     * 构造方法。
     *
     * @param jdbcTemplate            JDBC 模板
     * @param stringRedisTemplate     Redis 字符串模板
     * @param elasticsearchClient     Elasticsearch 客户端
     * @param minioClient             MinIO 客户端
     * @param knowledgeBaseProperties 知识库配置属性
     * @param kafkaAdmin              Kafka 管理器
     */
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
        // 1. 分别检查各依赖组件状态
        String mysqlStatus = checkMysql();
        String redisStatus = checkRedis();
        String elasticsearchStatus = checkElasticsearch();
        String minioStatus = checkMinioConfig();
        String kafkaStatus = checkKafkaConfig();
        
        // 2. 计算总体健康状态
        String overallStatus = resolveOverallStatus(
                mysqlStatus,
                redisStatus,
                elasticsearchStatus,
                minioStatus,
                kafkaStatus
        );
        
        // 3. 构造状态描述信息
        String message = buildMessage(
                mysqlStatus,
                redisStatus,
                elasticsearchStatus,
                minioStatus,
                kafkaStatus
        );
        
        log.info("[KB][HEALTH] 健康检查完成 - status={}, mysql={}, redis={}, es={}, minio={}, kafka={}",
                overallStatus, mysqlStatus, redisStatus, elasticsearchStatus, minioStatus, kafkaStatus);

        // 4. 构建并返回健康检查响应
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
        // 所有核心组件均为 UP 时返回 UP，否则返回 DOWN
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
        // 拼接各组件状态为可读字符串
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
            // 1. 校验 Kafka 配置节点是否存在
            if (knowledgeBaseProperties == null || knowledgeBaseProperties.getKafka() == null) {
                return "DOWN";
            }

            // 2. 校验文档处理 Topic 是否已配置
            if (!hasText(knowledgeBaseProperties.getKafka().getTopicDocumentProcess())) {
                return "DOWN";
            }

            // 3. 尝试初始化 KafkaAdmin，成功则视为可用
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
            // 1. 校验 MinIO 配置节点是否存在
            if (knowledgeBaseProperties == null || knowledgeBaseProperties.getMinio() == null) {
                return "DOWN";
            }

            // 2. 校验存储桶名称是否已配置
            String bucketName = knowledgeBaseProperties.getMinio().getBucketName();
            if (!hasText(bucketName)) {
                return "DOWN";
            }

            // 3. 检查存储桶是否存在
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
            // 发送 ping 请求判断 ES 是否可用
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
            // 尝试获取 Redis 连接并发送 ping 命令
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
            // 执行简单查询验证 MySQL 连接可用性
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
        // 判断字符串是否非空且非纯空白
        return value != null && !value.isBlank();
    }
}
