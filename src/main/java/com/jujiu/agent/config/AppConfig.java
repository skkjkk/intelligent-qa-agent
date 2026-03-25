package com.jujiu.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 10:26
 */
@Configuration
public class AppConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        // 1. 使用更好的工厂类（推荐 HttpClient，但 Simple 也行，关键是传进去）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(30000);

        RestTemplate restTemplate = new RestTemplate(factory);

        // 2. 解决和风天气 Gzip 压缩导致乱码/解析失败的问题
        // 如果不加这个，即使 200 了，你拿到的 Map 也可能是空的或报错
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        return restTemplate;
    }
}
