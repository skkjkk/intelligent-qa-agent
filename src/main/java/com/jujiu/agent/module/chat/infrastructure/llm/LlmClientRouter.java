package com.jujiu.agent.module.chat.infrastructure.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:59
 */
@Component
@Slf4j
public class LlmClientRouter {
    /**
     * 模型提供方名称到模型客户端的映射
     */
    private final Map<String, LlmClient> clients;
    /**
     * 默认模型提供方名称
     */
    private final String defaultProvider;

    /**
     * 构造函数
     * @param clients 模型客户端列表
     * @param defaultProvider 默认模型提供方名称
     */
    public LlmClientRouter(List<LlmClient> clients,
                           @Value("${llm.default-provider:deepseek}") String defaultProvider) {
        this.clients = clients.stream()
                .collect(Collectors
                        .toMap(LlmClient::getProviderName, Function.identity()));
        
        this.defaultProvider = defaultProvider;
        log.info("[LLM_ROUTER] 已注册 providers: {}", this.clients.keySet());
    }

    /**
     * 获取模型客户端
     * @param provider 模型提供方名称
     * @return 模型客户端
     */
    public LlmClient get(String provider) {
        if (provider == null || provider.isBlank()) {
            log.info("[LLM_ROUTER] 未指定 provider，使用默认 provider - defaultProvider={}", defaultProvider);
            return getDefault();
        }

        LlmClient client = clients.get(provider);
        if (client != null) {
            return client;
        }

        log.warn("[LLM_ROUTER] 未找到指定 provider，回退默认 provider - provider={}, defaultProvider={}",
                provider, defaultProvider);
        return getDefault();
    }

    /**
     * 获取默认模型客户端
     * @return 默认模型客户端
     */
    public LlmClient getDefault() {
        LlmClient client = clients.get(defaultProvider);
        if (client == null) {
            throw new IllegalStateException("默认 LLM provider 未注册: " + defaultProvider);
        }
        return client;
    }
}
