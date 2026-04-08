package com.jujiu.agent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Elasticsearch 客户端配置类。
 *
 * <p>负责根据 Spring Elasticsearch 配置创建 Java Client。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/8
 */
@Configuration
public class ElasticsearchConfig {

    /**
     * 创建低级别 RestClient。
     *
     * @param elasticsearchProperties Spring Elasticsearch 配置
     * @return RestClient 实例
     */
    @Bean
    public RestClient restClient(ElasticsearchProperties elasticsearchProperties) {
        List<String> uris = elasticsearchProperties.getUris();
        String uri = (uris == null || uris.isEmpty()) ? "http://localhost:9201" : uris.get(0);
        return RestClient.builder(HttpHost.create(uri)).build();
    }

    /**
     * 创建 Elasticsearch 传输层对象。
     *
     * @param restClient 低级别 RestClient
     * @return ElasticsearchTransport 实例
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * 创建 ElasticsearchClient。
     *
     * @param transport Elasticsearch 传输层
     * @return ElasticsearchClient 实例
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
