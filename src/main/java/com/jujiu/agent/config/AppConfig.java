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
        
        // 创建HTTP连接工厂，专门用来配置超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 连接超时：3秒（连接 DeepSeek 服务器的最长等待时间）
        factory.setConnectTimeout(3000);
        
        // 读取超时：3秒（读取 DeepSeek 服务器响应的最长等待时间）
        factory.setReadTimeout(3000);

        // 读取超时：30秒（读取 DeepSeek 服务器响应的最长等待时间）
        factory.setReadTimeout(30000);
        
        return new RestTemplate();
    }
}
