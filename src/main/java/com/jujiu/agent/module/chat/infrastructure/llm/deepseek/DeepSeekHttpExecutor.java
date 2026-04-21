package com.jujiu.agent.module.chat.infrastructure.llm.deepseek;

import com.jujiu.agent.infrastructure.config.WebClientConfig;
import com.jujiu.agent.module.chat.infrastructure.config.DeepSeekProperties;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekRequest;
import com.jujiu.agent.module.chat.infrastructure.llm.deepseek.dto.DeepSeekResponse;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

/**
 * DeepSeek HTTP 执行器。
 * <p>
 * 专门负责同步 HTTP 请求和流式 SSE 请求，避免 Provider 门面直接操作网络细节。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeepSeekHttpExecutor {

    /** 阻塞式 HTTP 客户端。 */
    private final RestTemplate restTemplate;

    /** DeepSeek 配置。 */
    private final DeepSeekProperties deepSeekProperties;

    /** WebClient 构造器。 */
    private final WebClientConfig webClientConfig;

    /**
     * 执行普通同步对话。
     *
     * @param request DeepSeek 请求
     * @return DeepSeek 响应
     */
    public DeepSeekResponse executeChat(DeepSeekRequest request) {
        log.info("[LLM][DEEPSEEK][CHAT] 开始普通对话 - model={}, messageCount={}, baseUrl={}",
                request.getModel(),
                request.getMessages() == null ? 0 : request.getMessages().size(),
                deepSeekProperties.getBaseUrl());
        return post(request, "CHAT");
    }

    /**
     * 执行带工具同步对话。
     *
     * @param request DeepSeek 请求
     * @return DeepSeek 响应
     */
    public DeepSeekResponse executeToolChat(DeepSeekRequest request) {
        log.info("[LLM][DEEPSEEK][CHAT_WITH_TOOLS] 开始带工具对话 - model={}, messageCount={}, toolCount={}",
                request.getModel(),
                request.getMessages() == null ? 0 : request.getMessages().size(),
                request.getTools() == null ? 0 : request.getTools().size());
        return post(request, "CHAT_WITH_TOOLS");
    }

    /**
     * 执行普通流式对话。
     *
     * @param request DeepSeek 请求
     * @return 原始 SSE 行流
     */
    public Flux<String> streamChat(DeepSeekRequest request) {
        log.info("[LLM][DEEPSEEK][STREAM_CHAT] 开始普通流式对话 - model={}, messageCount={}",
                request.getModel(),
                request.getMessages() == null ? 0 : request.getMessages().size());
        return postStream(request);
    }

    /**
     * 执行带工具流式对话。
     *
     * @param request DeepSeek 请求
     * @return 原始 SSE 行流
     */
    public Flux<String> streamToolChat(DeepSeekRequest request) {
        log.info("[LLM][DEEPSEEK][STREAM_CHAT_WITH_TOOLS] 开始流式工具对话 - model={}, messageCount={}, toolCount={}",
                request.getModel(),
                request.getMessages() == null ? 0 : request.getMessages().size(),
                request.getTools() == null ? 0 : request.getTools().size());
        return postStream(request);
    }

    /**
     * 发起同步 POST 请求。
     *
     * @param request 请求体
     * @param scenario 场景标识
     * @return DeepSeek 响应
     */
    private DeepSeekResponse post(DeepSeekRequest request, String scenario) {
        long startTime = System.currentTimeMillis();
        DeepSeekResponse response = restTemplate.postForObject(
                deepSeekProperties.getBaseUrl() + "/chat/completions",
                new HttpEntity<>(request, buildJsonHeaders()),
                DeepSeekResponse.class
        );
        long costTime = System.currentTimeMillis() - startTime;

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.error("[LLM][DEEPSEEK][{}] API 返回空响应", scenario);
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
        }

        log.info("[LLM][DEEPSEEK][{}] 请求完成 - totalTokens={}, costTime={}ms",
                scenario,
                response.getUsage() != null ? response.getUsage().getTotalTokens() : 0,
                costTime);
        return response;
    }

    /**
     * 发起流式 POST 请求。
     *
     * @param request 请求体
     * @return 原始 SSE 行
     */
    private Flux<String> postStream(DeepSeekRequest request) {
        return webClientConfig.webClientBuilder().build()
                .post()
                .uri(deepSeekProperties.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line != null && !line.isEmpty());
    }

    /**
     * 构建 JSON 请求头。
     *
     * @return HTTP 请求头
     */
    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekProperties.getApiKey());
        return headers;
    }
}
