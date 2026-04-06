package com.jujiu.agent.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * MinIO 客户端配置。
 *
 * <p>将 MinIO SDK 的初始化与业务逻辑解耦，
 * 使 {@code MinioFileService} 专注于文件存取业务。
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 10:19
 */
@Configuration
public class MinioConfig {

    /**
     * 创建 MinIO 客户端。
     *
     * @param properties 知识库配置，包含 MinIO 连接参数
     * @return 配置好的 {@link MinioClient}
     */
    @Bean
    public MinioClient minioClient(KnowledgeBaseProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(
                        properties.getMinio().getAccessKey(),
                        properties.getMinio().getSecretKey()
                )
                .build();
    }
}
