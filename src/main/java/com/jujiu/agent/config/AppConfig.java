package com.jujiu.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory();
        // 5秒连接超时
        factory.setConnectTimeout(5000);
        // 10秒读取超时
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
