package com.jujiu.agent.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 17:41
 */
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        
        // 1. 创建HttpClient，并设置相关参数
        HttpClient httpClient = HttpClient.create()
                // 1.1 设置响应超时时间为30秒
                .responseTimeout(Duration.ofSeconds(30))
                // 1.2 设置连接超时为5秒
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        
        // 2.  创建 ReactorClientHttpConnector 并传入配置
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        
        // 3. 返回配置了clientConnector的WebClient.Builder
        return WebClient.builder().clientConnector(connector);
    }
    
}
