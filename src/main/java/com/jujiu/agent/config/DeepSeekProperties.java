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
    
    /**
     * 系统提示
     * 用于设置对话的初始场景和角色
     * 示例：你是一个乐于助人、知识渊博且诚实的智能助手。你的目标是为用户提供准确、清晰、有用的解答。
     */
    private String systemPrompt;

    /**
     * 温度参数，控制回复的随机性
     * 范围：0.0 - 2.0
     * 越低越确定（适合代码、数学），越高越创意（适合写作、头脑风暴）
     * 默认：0.7
     */
    private Double temperature = 0.7;
}
