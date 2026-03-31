package com.jujiu.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import org.springframework.web.client.RestTemplate;


/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 10:26
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 10秒连接超时
        factory.setConnectTimeout(10000);
        // 120秒读取超时（AI处理需要较长时间）
        factory.setReadTimeout(120000); 
        return new RestTemplate(factory);
    }
}
