package com.jujiu.agent.module.kb.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库配置属性。
 *
 * <p>统一承接知识库相关配置：
 * <ul>
 *     <li>embedding</li>
 *     <li>elasticsearch</li>
 *     <li>chunking</li>
 *     <li>rag</li>
 *     <li>rerank</li>
 *     <li>security</li>
 * </ul>
 *
 * @author 17644
 * @since 2026/3/31
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
    
    private Rerank rerank = new Rerank();

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
        
        /**
         * 是否启用
         */
        private Boolean enabled = true;

        /**
         * 提供商
         */
        private String provider = "zhipu";

        /**
         * 超时时间（毫秒）
         */
        private Long timeoutMs = 8000L;

        private Retry retry = new Retry();
        private Cache cache = new Cache();
        private Degrade degrade = new Degrade();
        
        @Data
        public static class Retry {
            private Boolean enabled = true;
            private Integer maxAttempts = 3;
            private Long backoffMs = 300L;
            private Long maxBackoffMs = 1500L;
        }

        @Data
        public static class Cache {
            private Boolean enabled = true;
            private Long ttlHours = 24L;
            private String keyPrefix = "kb:embedding";
        }

        @Data
        public static class Degrade {
            private Boolean allowCacheReadOnRemoteFailure = true;
            private Boolean allowDocumentIndexContinue = true;
        }
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

        private Health health = new Health();

        @Data
        public static class Health {
            private Boolean writeCheckEnabled = true;
            private String probeObjectPrefix = "_health";
        }
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

        private Health health = new Health();

        @Data
        public static class Health {
            private String topic = "kb-health-check";
            private Long adminTimeoutMs = 3000L;
            private Long sendTimeoutMs = 3000L;
            private Boolean sendCheckEnabled = true;
        }
    }
    
    @Data
    public static class Chunking {
        /**
         * 默认分块大小
         */
        private Integer defaultSize = 700;

        /**
         * 默认重叠大小
         */
        private Integer defaultOverlap = 80;

        /**
         * 最大分块大小
         */
        private Integer maxSize = 1200;
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
    public static class Rerank {

        /**
         * 是否启用独立 rerank 服务。
         *
         * <p>注意：该开关是检索层 rerank 主开关，
         * 不建议再复用 rag.enableRerank 承载实现细节。
         */
        private Boolean enabled = false;

        /**
         * rerank 服务地址。
         */
        private String apiUrl;

        /**
         * rerank 服务 API Key。
         */
        private String apiKey;

        /**
         * rerank 模型名称。
         */
        private String model = "bge-reranker-v2-m3";

        /**
         * rerank 后最多保留的候选数量。
         */
        private Integer topN = 10;

        /**
         * 送入 rerank 的最大候选数。
         *
         * <p>过大只会浪费成本，过小会限制召回上界。
         */
        private Integer maxCandidates = 20;

        /**
         * rerank 分数阈值。
         *
         * <p>小于该阈值的候选会被过滤。
         */
        private Double scoreThreshold = 0D;
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

