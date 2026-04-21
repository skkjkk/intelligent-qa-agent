package com.jujiu.agent.module.kb.application.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.jujiu.agent.module.kb.infrastructure.config.KnowledgeBaseProperties;
import com.jujiu.agent.module.kb.api.response.KbHealthComponentDetail;
import com.jujiu.agent.module.kb.api.response.KbHealthResponse;
import com.jujiu.agent.module.kb.application.service.KnowledgeBaseHealthService;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    /** Kafka 模板，用于发送健康检查消息。 */
    private final KafkaTemplate<String, Object> kafkaTemplate;

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
                                          KafkaAdmin kafkaAdmin,
                                          KafkaTemplate<String, Object> kafkaTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.elasticsearchClient = elasticsearchClient;
        this.minioClient = minioClient;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * 执行知识库健康检查。
     * 
     * <p>该方法检查知识库系统依赖的所有组件的可用性，包括：
     * <ul>
     *     <li>MySQL - 文档和元数据存储</li>
     *     <li>Redis - 缓存和分布式锁</li>
     *     <li>Elasticsearch - 向量检索和全文搜索</li>
     *     <li>MinIO - 文件对象存储</li>
     *     <li>Kafka - 消息队列</li>
     * </ul>
     * 
     * <p>执行流程：
     * <ol>
     *     <li>分别检查各依赖组件状态（checkMysql、checkRedis等）</li>
     *     <li>将组件状态明细合并为Map便于查询</li>
     *     <li>解析总体健康状态（所有组件UP则为UP，否则为DOWN）</li>
     *     <li>生成问题描述消息</li>
     *     <li>构建并返回健康检查响应</li>
     * </ol>
     * 
     * <p>状态说明：
     * <ul>
     *     <li>UP - 组件正常</li>
     *     <li>DOWN - 组件不可用</li>
     *     <li>DEGRADED - 组件降级（部分功能可用）</li>
     * </ul>
     *
     * @return 健康检查结果，包含各组件状态和总体状态
     */
    @Override
    public KbHealthResponse checkHealth() {
        // 1. 分别检查各依赖组件状态
        List<KbHealthComponentDetail> details = new ArrayList<>();
        details.add(checkMysql());
        details.add(checkRedis());
        details.add(checkElasticsearch());
        details.add(checkMinio());
        details.add(checkKafka());

        // 2. 合并组件状态明细为Map
        Map<String, KbHealthComponentDetail> map = details.stream()
                .collect(Collectors.toMap(KbHealthComponentDetail::getComponent, d -> d));

        // 3. 解析总体状态（所有UP则为UP，否则为DOWN）
        String status = resolveOverallStatus(details);

        // 4. 生成问题描述消息
        String message = details.stream()
                .filter(d -> !"UP".equals(d.getStatus()))
                .map(d -> d.getComponent() + "=" + d.getStatus() + (d.getReason() == null ? "" : "(" + d.getReason() + ")"))
                .collect(Collectors.joining(", "));

        if (message.isBlank()) {
            message = "all components healthy";
        }

        // 5. 构建并返回响应
        return KbHealthResponse.builder()
                .status(status)
                .mysqlStatus(statusOf(map, "mysql"))
                .redisStatus(statusOf(map, "redis"))
                .elasticsearchStatus(statusOf(map, "elasticsearch"))
                .minioStatus(statusOf(map, "minio"))
                .kafkaStatus(statusOf(map, "kafka"))
                .message(message)
                .details(details)
                .build();
    }

    private String statusOf(Map<String, KbHealthComponentDetail> map, String key) {
        KbHealthComponentDetail detail = map.get(key);
        return detail == null ? "DOWN" : detail.getStatus();
    }
    
    /**
     * 解析总体状态。
     *
     * @param details 组件状态明细列表
     * @return 总体状态
     */
    private String resolveOverallStatus(List<KbHealthComponentDetail> details) {
        // 所有核心组件均为 UP 时返回 UP，否则返回 DOWN
        boolean anyDown = details.stream().anyMatch(d -> "DOWN".equals(d.getStatus()));
        if (anyDown) {
            return "DOWN";
        }
        
        // 有核心组件为 DEGRADED 时返回 DEGRADED，否则返回 UP
        boolean anyDegraded = details.stream().anyMatch(d -> "DEGRADED".equals(d.getStatus()));
        if (anyDegraded) {
            return "DEGRADED";
        }
        return "UP";
    }

    /**
     * 检查Kafka组件健康状态
     * 
     * <p>Kafka健康检查分为两个层次：
     * <ol>
     *     <li>连接性检查：使用AdminClient获取集群ID</li>
     *     <li>功能性检查：尝试发送测试消息到指定Topic</li>
     * </ol>
     * 
     * <p>检查结果判定：
     * <ul>
     *     <li>DOWN - 配置缺失、连接失败、无法获取clusterId</li>
     *     <li>DEGRADED - 连接正常但消息发送超时</li>
     *     <li>UP - 连接正常且消息发送成功</li>
     * </ul>
     * 
     * <p>注意：
     * <ul>
     *     <li>检查会创建临时的AdminClient，使用后必须在finally中关闭</li>
     *     <li>使用3秒超时避免健康检查长时间阻塞</li>
     *     <li>测试消息发送到专门的健康检查Topic，不会影响业务数据</li>
     * </ul>
     * 
     * @return Kafka组件的健康状态详情
     */
    private KbHealthComponentDetail checkKafka() {
        long start = System.currentTimeMillis();
        org.apache.kafka.clients.admin.AdminClient adminClient = null;
        try {
            // 1. 检查Kafka配置是否存在
            if (knowledgeBaseProperties.getKafka() == null) {
                return detail("kafka", "DOWN", "connectivity", start, "kafka config missing");
            }
            
            // 2. 创建AdminClient并获取集群ID（连接性检查）
            adminClient = org.apache.kafka.clients.admin.AdminClient.create(kafkaAdmin.getConfigurationProperties());
            String clusterId = adminClient.describeCluster().clusterId().get(3, TimeUnit.SECONDS);

            // 3. 验证clusterId不为空
            if (!hasText(clusterId)) {
                return detail("kafka", "DOWN", "connectivity", start, "clusterId empty");
            }
            
            // 4. 发送测试消息（功能性检查）
            kafkaTemplate.send("kb-health-check", "__health__", "ping").get(3, TimeUnit.SECONDS);
            
            // 5. 所有检查通过，返回UP
            return detail("kafka", "UP", "capability", start, null);
        } catch (java.util.concurrent.TimeoutException e) {
            // 6. 超时返回DEGRADED（连接正常但性能有问题）
            log.warn("[KB][HEALTH] kafka send timeout", e);
            return detail("kafka", "DEGRADED", "capability", start, "send timeout");
        } catch (Exception e) {
            // 7. 其他异常返回DOWN
            log.warn("[KB][HEALTH] kafka check failed", e);
            return detail("kafka", "DOWN", "capability", start, e.getMessage());
        } finally {
            // 8. 必须在finally中关闭AdminClient
            if (adminClient != null) {
                try {
                    adminClient.close();
                } catch (Exception ignore) {
                }
            }
        }
    }


    /**
     * 检查MinIO组件健康状态
     * 
     * <p>MinIO健康检查分为三个层次：
     * <ol>
     *     <li>配置检查：验证bucket配置是否存在</li>
     *     <li>存在性检查：验证指定的bucket是否存在</li>
     *     <li>功能性检查：执行完整的写-读-删操作</li>
     * </ol>
     * 
     * <p>功能性检查流程（完整CRUD验证）：
     * <ol>
     *     <li>写入测试对象（PUT）</li>
     *     <li>获取对象元数据（STAT）验证写入成功</li>
     *     <li>删除测试对象（REMOVE）清理测试数据</li>
     * </ol>
     * 
     * <p>检查结果判定：
     * <ul>
     *     <li>DOWN - 配置缺失、bucket不存在、操作失败</li>
     *     <li>DEGRADED - 操作执行但结果不符合预期</li>
     *     <li>UP - 完整的写-读-删操作成功</li>
     * </ul>
     * 
     * <p>注意：
     * <ul>
     *     <li>测试文件存储在"_health/"路径下，不会影响业务数据</li>
     *     <li>测试完成后会立即删除测试文件，清理现场</li>
     *     <li>使用1字节的测试数据，最小化资源占用</li>
     * </ul>
     * 
     * @return MinIO组件的健康状态详情
     */
    private KbHealthComponentDetail checkMinio() {
        long start = System.currentTimeMillis();
        try {
            // 1. 检查MinIO配置是否存在
            if (knowledgeBaseProperties.getMinio() == null || !hasText(knowledgeBaseProperties.getMinio().getBucketName())) {
                return detail("minio", "DOWN", "connectivity", start, "minio bucket config missing");
            }

            // 2. 检查MinIO bucket是否存在
            String bucket = knowledgeBaseProperties.getMinio().getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                return detail("minio", "DOWN", "connectivity", start, "bucket not exists: " + bucket);
            }

            // 3. 生成测试对象路径（使用UUID避免冲突）
            String object = "_health/" + UUID.randomUUID() + ".txt";
            byte[] data = new byte[]{1};

            // 4. 写入测试对象（PUT）
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("text/plain")
                    .build());

            // 5. 获取对象元数据（STAT）
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .build());

            // 6. 删除测试对象（REMOVE）
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .build());

            // 7. 验证写入成功
            if (stat == null || stat.size() != 1L) {
                return detail("minio", "DEGRADED", "capability", start, "put/stat/remove verify failed");
            }

            // 8. 所有检查通过，返回UP
            return detail("minio", "UP", "capability", start, null);
        } catch (Exception e) {
            log.warn("[KB][HEALTH] minio check failed", e);
            return detail("minio", "DOWN", "capability", start, e.getMessage());
        }
    }

    /**
     * 检查 Elasticsearch 状态。
     *
     * @return 状态字符串
     */
    private KbHealthComponentDetail checkElasticsearch() {
        long start = System.currentTimeMillis();
        try {
            // 1. 检查 Elasticsearch 连接是否可用
            boolean ping = elasticsearchClient.ping().value();
            
            // 2. 返回连接状态
            return ping
                    ? detail("elasticsearch", "UP", "connectivity", start, null)
                    : detail("elasticsearch", "DOWN", "connectivity", start, "ping=false");
        } catch (Exception e) {
            log.warn("[KB][HEALTH] elasticsearch check failed", e);
            return detail("elasticsearch", "DOWN", "connectivity", start, e.getMessage());
        }
    }

    /**
     * 检查 Redis 状态。
     *
     * @return 状态字符串
     */
    private KbHealthComponentDetail checkRedis() {
        long start = System.currentTimeMillis();
        RedisConnection connection = null;
        try {
            // 1. 检查 Redis 连接工厂是否存在
            if (stringRedisTemplate.getConnectionFactory() == null) {
                return detail("redis", "DOWN", "connectivity", start, "connectionFactory is null");
            }
            
            // 2. 检查 Redis 连接是否可用
            connection = stringRedisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            if (pong == null) {
                return detail("redis", "DOWN", "connectivity", start, "ping response is null");
            }
            
            // 3. 返回连接状态
            return detail("redis", "UP", "connectivity", start, null);
        } catch (Exception e) {
            log.warn("[KB][HEALTH] redis check failed", e);
            return detail("redis", "DOWN", "connectivity", start, e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * 检查 MySQL 状态。
     *
     * @return 状态字符串
     */
    private KbHealthComponentDetail checkMysql() {
        long start = System.currentTimeMillis();
        try {
            // 1. 执行简单的查询 查询 1
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            
            // 2. 返回连接状态
            return detail("mysql", "UP", "connectivity", start, null);
        } catch (Exception e) {
            log.warn("[KB][HEALTH] mysql check failed", e);
            return detail("mysql", "DOWN", "connectivity", start, e.getMessage());
        }
    }

    /**
     * 构建健康检查组件详情对象
     * 
     * <p>这是一个工具方法，用于封装健康检查的结果信息。
     * 统一了组件详情的构建方式，便于各组件检查方法使用。
     * 
     * <p>包含的字段：
     * <ul>
     *     <li>component - 组件名称（如mysql、redis、elasticsearch等）</li>
     *     <li>status - 健康状态（UP、DOWN、DEGRADED）</li>
     *     <li>checkType - 检查类型（connectivity、capability等）</li>
     *     <li>latencyMs - 检查耗时（毫秒）</li>
     *     <li>reason - 原因描述（状态异常时的详细说明）</li>
     *     <li>timestamp - 检查时间戳</li>
     * </ul>
     * 
     * @param component 组件名称
     * @param status 健康状态
     * @param checkType 检查类型
     * @param start 检查开始时间（用于计算耗时）
     * @param reason 原因描述（状态异常时填写，正常时为null）
     * @return 健康检查组件详情对象
     */
    private KbHealthComponentDetail detail(String component, 
                                           String status, 
                                           String checkType, 
                                           long start, 
                                           String reason) {
        return KbHealthComponentDetail.builder()
                .component(component) // 组件名称
                .status(status) // 健康状态
                .checkType(checkType) // 检查类型
                .latencyMs(System.currentTimeMillis() - start) // 检查耗时（毫秒）
                .reason(reason) // 原因描述（状态异常时填写详细说明）
                .timestamp(java.time.LocalDateTime.now()) // 检查时间戳
                .build();
    }

    /**
     * 判断字符串是否有实际文本内容
     * 
     * <p>这是一个工具方法，用于判断字符串是否为null或空字符串。
     * 比单纯的null检查更严格，确保字符串包含非空白字符。
     * 
     * <p>判断逻辑：
     * <ul>
     *     <li>value == null → false</li>
     *     <li>value == "" → false</li>
     *     <li>value == "   "（仅空白字符）→ false</li>
     *     <li>value == "abc" → true</li>
     * </ul>
     * 
     * <p>等价于Spring Framework的StringUtils.hasText()方法
     * 
     * @param value 需要检查的字符串
     * @return 如果字符串有实际内容返回true，否则返回false
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}