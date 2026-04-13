package com.jujiu.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/31 17:30
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge-base")
public class KnowledgeBaseProperties {
    
    private Embedding embedding = new Embedding();

    private Elasticsearch elasticsearch = new Elasticsearch();

    private Minio minio = new Minio();

    private Kafka kafka = new Kafka();

    private Chunking chunking = new Chunking();

    private Rag rag = new Rag();

    private Security security = new Security();

    @Data
    public static class Embedding {

        /**
         * API地址
         */
        private String apiUrl;

        /**
         * API密钥
         */
        private String apiKey;

        /**
         * 模型
         */
        private String model;

        /**
         * 向量维度
         */
        private Integer dimension = 2048;
    }

    @Data
    public static class Elasticsearch {
        
        /**
         * 索引名称
         */
        private String indexName = "kb_chunks_v2";
        /**
         * 默认检索 TopK
         */
        private Integer topK = 5;

        /**
         * 是否启用混合搜索
         */
        private Boolean enableHybrid = true;
    }
    
    @Data
    public static class Minio {
        /**
         * Minio地址
         */
        private String endpoint;

        /**
         * Minio访问密钥
         */
        private String accessKey;

        /**
         * Minio秘密密钥
         */
        private String secretKey;

        /**
         * Minio存储桶名称
         */
        private String bucketName;
    }
    
    @Data
    public static class Kafka {
        /**
         * 文档处理主题
         */
        private String topicDocumentProcess = "kb-document-processor";

        /**
         * 消费者并发数
         */
        private Integer consumerConcurrency = 1;

        /**
         * 文档索引主题。
         */
        private String topicDocumentIndex = "kb-document-index";
    }
    
    @Data
    public static class Chunking {
        /**
         * 默认分块大小
         */
        private Integer defaultSize = 500;

        /**
         * 默认重叠大小
         */
        private Integer defaultOverlap = 50;

        /**
         * 最大分块大小
         */
        private Integer maxSize = 1000;
    }
    
    @Data
    public static class Rag {
        /**
         * 最大上下文块数
         */
        private Integer maxContextChunks = 5;

        /**
         * 是否启用 rerank
         */
        private Boolean enableRerank = false;

        /**
         * RAG Prompt 模板
         */
        private String promptTemplate;
    }
    
    @Data
    public static class Security {
        /**
         * 是否启用 ACL
         */
        private Boolean enableAcl = true;

        /**
         * 查询限流阈值
         */
        private Integer queryRateLimit = 30;
    }
}

