package com.jujiu.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek API 配置属性类
 * 
 * 用于从 application.yml 中读取 DeepSeek 相关配置
 * 使用 @ConfigurationProperties 实现类型安全的配置绑定
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 9:59
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    
    /**
     * DeepSeek API 密钥
     * 用于身份验证和授权
     * 示例：sk-xxxxxxxxxxxxxxxxxxxxxxxx
     */
    private String apiKey;
    
    /**
     * DeepSeek API 基础 URL
     * API 服务的访问地址
     * 示例：https://api.deepseek.com/v1
     */
    private String baseUrl;
    
    /**
     * 使用的模型名称
     * 指定要调用的 DeepSeek 模型版本
     * 示例：deepseek-chat, deepseek-coder
     */
    private String model;
    
    /**
     * 最大上下文消息数
     * 控制对话历史中保留的消息数量
     * 用于限制 token 消耗和保持对话连贯性
     * 示例：10
     */
    private int maxContextMessages;
    
    /**
     * 限流配置
     * 用于控制 API 调用的频率限制
     */
    private int maxMessagesPerMinute;
}
