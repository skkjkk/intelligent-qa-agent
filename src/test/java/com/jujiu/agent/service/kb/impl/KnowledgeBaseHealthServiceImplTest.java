//package com.jujiu.agent.service.kb.impl;
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import co.elastic.clients.elasticsearch.core.PingResponse;
//import com.jujiu.agent.config.KnowledgeBaseProperties;
//import com.jujiu.agent.module.kb.api.response.KbHealthResponse;
//import io.minio.BucketExistsArgs;
//import io.minio.MinioClient;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.data.redis.connection.RedisConnection;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.kafka.core.KafkaAdmin;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
///**
// * 知识库健康检查服务单元测试。
// *
// * <p>用于验证 MySQL、Redis、Elasticsearch、MinIO 与 Kafka
// * 健康状态聚合逻辑。
// *
// * @author 17644
// * @version 1.0.0
// * @since 2026/4/11
// */
//class KnowledgeBaseHealthServiceImplTest {
//
//    private JdbcTemplate jdbcTemplate;
//    private StringRedisTemplate stringRedisTemplate;
//    private ElasticsearchClient elasticsearchClient;
//    private KnowledgeBaseProperties knowledgeBaseProperties;
//    private MinioClient minioClient;
//    private KafkaAdmin kafkaAdmin;
//
//    private KnowledgeBaseHealthServiceImpl healthService;
//
//    @BeforeEach
//    void setUp() {
//        jdbcTemplate = mock(JdbcTemplate.class);
//        stringRedisTemplate = mock(StringRedisTemplate.class);
//        elasticsearchClient = mock(ElasticsearchClient.class);
//        knowledgeBaseProperties = new KnowledgeBaseProperties();
//        minioClient = mock(MinioClient.class);
//        kafkaAdmin = mock(KafkaAdmin.class);
//
//        KnowledgeBaseProperties.Minio minio = new KnowledgeBaseProperties.Minio();
//        minio.setEndpoint("http://localhost:9000");
//        minio.setBucketName("intelligent-qa-kb");
//        knowledgeBaseProperties.setMinio(minio);
//
//        KnowledgeBaseProperties.Kafka kafka = new KnowledgeBaseProperties.Kafka();
//        kafka.setTopicDocumentProcess("kb-document-process");
//        knowledgeBaseProperties.setKafka(kafka);
//
//        healthService = new KnowledgeBaseHealthServiceImpl(
//                jdbcTemplate,
//                stringRedisTemplate,
//                elasticsearchClient,
//                minioClient,
//                knowledgeBaseProperties,
//                kafkaAdmin
//        );
//    }
//
//    @Test
//    @DisplayName("checkHealth 所有依赖正常时应返回 UP")
//    void shouldReturnUpWhenAllDependenciesHealthy() throws Exception {
//        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
//
//        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
//        RedisConnection redisConnection = mock(RedisConnection.class);
//        when(stringRedisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
//        when(connectionFactory.getConnection()).thenReturn(redisConnection);
//        when(redisConnection.ping()).thenReturn("PONG");
//
//        PingResponse pingResponse = mock(PingResponse.class);
//        when(pingResponse.value()).thenReturn(true);
//        when(elasticsearchClient.ping()).thenReturn(pingResponse);
//
//        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
//
//        doNothing().when(kafkaAdmin).initialize();
//
//        KbHealthResponse result = healthService.checkHealth();
//
//        assertNotNull(result);
//        assertEquals("UP", result.getStatus());
//        assertEquals("UP", result.getMysqlStatus());
//        assertEquals("UP", result.getRedisStatus());
//        assertEquals("UP", result.getElasticsearchStatus());
//        assertEquals("UP", result.getMinioStatus());
//        assertEquals("UP", result.getKafkaStatus());
//    }
//
//    @Test
//    @DisplayName("checkHealth 当 Elasticsearch ping 为 false 时应返回 DOWN")
//    void shouldReturnDownWhenElasticsearchPingFalse() throws Exception {
//        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
//
//        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
//        RedisConnection redisConnection = mock(RedisConnection.class);
//        when(stringRedisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
//        when(connectionFactory.getConnection()).thenReturn(redisConnection);
//        when(redisConnection.ping()).thenReturn("PONG");
//
//        PingResponse pingResponse = mock(PingResponse.class);
//        when(pingResponse.value()).thenReturn(false);
//        when(elasticsearchClient.ping()).thenReturn(pingResponse);
//
//        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
//
//        doNothing().when(kafkaAdmin).initialize();
//
//        KbHealthResponse result = healthService.checkHealth();
//
//        assertNotNull(result);
//        assertEquals("DOWN", result.getStatus());
//        assertEquals("DOWN", result.getElasticsearchStatus());
//    }
//}
