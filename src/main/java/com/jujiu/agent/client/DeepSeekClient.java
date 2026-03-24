package com.jujiu.agent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jujiu.agent.common.constant.BusinessConstants;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.DeepSeekProperties;
import com.jujiu.agent.config.WebClientConfig;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.deepseek.DeepSeekRequest;
import com.jujiu.agent.model.dto.deepseek.DeepSeekResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * DeepSeek API 客户端
 * 
 * 封装与 DeepSeek API 的交互逻辑
 * 提供对话、聊天等功能
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/23 10:28
 */
@Component
@Slf4j
public class DeepSeekClient {
    
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DeepSeekProperties deepSeekProperties;
    
    @Autowired
    private WebClientConfig webClientConfig;

    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 与 DeepSeek AI 进行对话
     *
     * @param messages 对话消息列表
     * @return AI 回复的内容
     */
    public DeepSeekResult chat(List<DeepSeekMessage> messages) {
        log.info("[DEEPSEEK][API_CALL] 开始调用 DeepSeek API - model={}, messageCount={}, baseUrl={}", 
                deepSeekProperties.getModel(), messages.size(), deepSeekProperties.getBaseUrl());
        
        // 1. 构建请求参数
        DeepSeekRequest deepSeekRequest = new DeepSeekRequest();
        deepSeekRequest.setModel(deepSeekProperties.getModel());
        deepSeekRequest.setMessages(messages);
        deepSeekRequest.setTemperature(deepSeekProperties.getTemperature());
        
        log.debug("[DEEPSEEK][REQUEST_BUILD] 请求参数构建完成 - model={}, messageCount={}", 
                deepSeekRequest.getModel(), deepSeekRequest.getMessages().size());
            
        // 2. 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekProperties.getApiKey());
        
        log.debug("[DEEPSEEK][HEADERS] 请求头构建完成 - contentType=application/json, apiKeyPrefix={}", 
                deepSeekProperties.getApiKey() != null && deepSeekProperties.getApiKey().length() > 10 
                        ? deepSeekProperties.getApiKey().substring(0, 10) + "..." : "unknown");
            
        // 3. 打包请求头 + 请求体
        HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(deepSeekRequest, headers);
        
        // 4. 发送 POST 请求
        log.info("[DEEPSEEK][SENDING_REQUEST] 正在发送请求到 DeepSeek API...");
        long startTime = System.currentTimeMillis();
        
        DeepSeekResponse response = restTemplate.postForObject(
                // 请求地址（baseUrl 拼上路径）
                deepSeekProperties.getBaseUrl() + "/chat/completions",
                // 请求体
                entity,
                // 响应类型
                DeepSeekResponse.class
        );
        
        long costTime = System.currentTimeMillis() - startTime;
        log.info("[DEEPSEEK][REQUEST_SENT] DeepSeek API 请求完成 - costTime={}ms, response={}", 
                costTime, response != null ? "not null" : "null");
            
        // 5. 空值检查，防止 NullPointerException
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.error("[DEEPSEEK][RESPONSE_ERROR] API 返回空响应 - response={}, choices={}", 
                    response, response != null ? response.getChoices() : "null");
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
        }
            
        // 6. 获取第一个选择 getChoices() → 取 choices 列表 .get(0) → 取第一个（DeepSeek 默认只返回一个）
        DeepSeekResponse.Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            log.error("[DEEPSEEK][PARSE_ERROR] API 返回的消息格式异常 - choice={}, message={}", 
                    choice, choice != null ? choice.getMessage() : "null"
            );
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_FORMAT_ERROR);
        }
        
        log.debug("[DEEPSEEK][PARSED] API 响应解析成功 - choiceIndex=0");
        
        // 7. 返回 AI 回复内容.getMessage() → 取消息对象  .getContent() → 取文本内容
        DeepSeekResponse.Usage usage = response.getUsage();
        int totalTokens = usage != null ? usage.getTotalTokens() : 0;
        int promptTokens = usage != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage != null ? usage.getCompletionTokens() : 0;
        
        log.info("[DEEPSEEK][CALL_COMPLETE] DeepSeek API 调用完成 - replyLength={}, totalTokens={}, promptTokens={}, completionTokens={}, costTime={}ms", 
                choice.getMessage().getContent().length(), totalTokens, promptTokens, completionTokens, costTime);
        
        return new DeepSeekResult(
                choice.getMessage().getContent(),
                totalTokens,
                promptTokens,
                completionTokens
        );
    }

    public Flux<String> chatStream(List<DeepSeekMessage> messages) {
        // 1. 构建请求体
        DeepSeekRequest request = new DeepSeekRequest();
        request.setMessages(messages);
        request.setModel(deepSeekProperties.getModel());
        
        // 2. 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekProperties.getApiKey());

        // 3. 设置流式标志和温度参数
        request.setStream(true);
        request.setTemperature(deepSeekProperties.getTemperature());
        
        // 4. 使用 WebClient 发送请求并获取流式响应
        return webClientConfig.webClientBuilder().build()
                .post()
                .uri(deepSeekProperties.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                // 返回 Flux<String>，每行一个 SSE 事件
                .bodyToFlux(String.class)
                // 过滤掉空行和 [DONE] 标记
                .filter(line -> line != null && !line.isEmpty() && !line.contains("[DONE]"))
                .map(line -> {
                    try {
                        // 去掉 "data: " 前缀
                        String data = line.startsWith("data: ") ? line.substring(6) : line;
                        // 再次检查是否为 [DONE]
                        if ("[DONE]".equals(data)) {
                            return "";
                        }
                        // 用 ObjectMapper 解析 JSON
                        StreamResponse response = objectMapper.readValue(data, StreamResponse.class);
                        // 增加空值检查
                        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                            return "";
                        }
                        // 返回 delta.content
                        StreamResponse.StreamDelta delta = response.getChoices().get(0).getDelta();
                        if (delta == null || delta.getContent() == null) {
                            return "";
                        }
                        return delta.getContent();

                    } catch (Exception e) {
                        log.error("[DEEPSEEK][STREAM_PARSE_ERROR] 流式响应解析错误 - line={}", line, e);
                        return "";
                    }
                })
                .filter(content -> content != null && !content.isEmpty()
                );
    }

    /**
     * 流式对话（带 Token 统计）
     * 返回 Flux<StreamResult>，包含内容片段和最后的 token 用量信息
     *
     * @param messages 对话消息列表
     * @return Flux<StreamResult> 流式响应结果
     */
    public Flux<StreamResult> chatStreamWithUsage(List<DeepSeekMessage> messages) {
        // 1. 构建请求体
        DeepSeekRequest request = new DeepSeekRequest();
        request.setMessages(messages);
        request.setModel(deepSeekProperties.getModel());
        request.setStream(true);
        request.setTemperature(deepSeekProperties.getTemperature());

        // 2. 使用 WebClient 发送请求并获取流式响应
        return webClientConfig.webClientBuilder().build()
                .post()
                .uri(deepSeekProperties.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                // 过滤空行和 [DONE]
                .filter(line -> line != null && !line.isEmpty() && !line.contains(BusinessConstants.SSE_DONE))
                .map(line -> {
                    try {
                        // 去掉 "data: " 前缀
                        String data = line.startsWith("data: ") ? line.substring(6) : line;
                        if (BusinessConstants.SSE_DONE.equals(data)) {
                            return StreamResult.end(null);
                        }

                        // 解析 JSON
                        StreamResponse response = objectMapper.readValue(data, StreamResponse.class);
                        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                            return StreamResult.content("");
                        }

                        StreamResponse.StreamChoice choice = response.getChoices().get(0);

                        // 调试日志：检查解析后的response结构
                        if (response.getUsage() != null) {
                            log.debug("[DEEPSEEK][STREAM_PARSE_DEBUG] 解析到usage信息 - promptTokens={}, completionTokens={}, totalTokens={}, finish_reason={}",
                                    response.getUsage().getPromptTokens(),
                                    response.getUsage().getCompletionTokens(),
                                    response.getUsage().getTotalTokens(),
                                    choice.getFinishReason());
                        }

                        // 检查是否是最后一条消息（有 usage 信息）
                        if (choice.getFinishReason() != null && "stop".equals(choice.getFinishReason())) {
                            // 最后一条，返回 token 信息
                            if (response.getUsage() != null) {
                                // 安全创建 StreamUsage，防止字段为 null
                                StreamResponse.StreamUsage usage = response.getUsage();
                                StreamResponse.StreamUsage safeUsage = new StreamResponse.StreamUsage(
                                        usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                                        usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
                                        usage.getTotalTokens() != null ? usage.getTotalTokens() : 0
                                );
                                return StreamResult.end(safeUsage);
                            }
                            return StreamResult.end(null);
                        }

                        // 普通内容消息
                        StreamResponse.StreamDelta delta = choice.getDelta();
                        if (delta == null || delta.getContent() == null) {
                            return StreamResult.content("");
                        }
                        return StreamResult.content(delta.getContent());

                    } catch (Exception e) {
                        log.error("[DEEPSEEK][STREAM_PARSE_ERROR] 流式响应解析错误 - line={}", line, e);
                        return StreamResult.content("");
                    }
                })
                .filter(result -> result != null && (result.isEnd() || (result.getContent() != null && !result.getContent().isEmpty())));
    }
    

    /**
     * 流式响应内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamResponse {
        // 流式响应ID
        private String id;
        // 对象类型
        private String object;
        // 创建时间
        private Long created;
        // 模型名称
        private String model;
        // 系统指纹
        private String systemFingerprint;
        // 流式选择列表
        private List<StreamChoice> choices;
        // Token 用量信息
        private StreamUsage usage;  // 添加 usage 字段，只在最后一条消息中有

        /**
         * 流式选择内部类
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StreamChoice {
            // 选择索引
            private Integer index;
            // 流式增量
            private StreamDelta delta;
            // 为 "stop" 时表示结束
            @JsonProperty("finish_reason")
            private String finishReason;  // 为 "stop" 时表示结束
        }

        /**
         * 流式增量内部类
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StreamDelta {
            // 角色
            private String role;
            // 内容
            private String content;
        }

        /**
         * Token 用量内部类（流式响应）
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StreamUsage {
            @JsonProperty("prompt_tokens")
            private Integer promptTokens;
            @JsonProperty("completion_tokens")
            private Integer completionTokens;
            @JsonProperty("total_tokens")
            private Integer totalTokens;
        }
    }

    /**
     * 流式响应结果包装类
     * 包含内容和可选的 token 用量信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamResult {
        private String content;  // 内容片段
        private StreamResponse.StreamUsage usage;  // token 用量（只有最后一条有）
        private boolean isEnd;  // 是否是最后一条

        public static StreamResult content(String content) {
            return new StreamResult(content, null, false);
        }

        public static StreamResult end(StreamResponse.StreamUsage usage) {
            return new StreamResult(null, usage, true);
        }
    }
}
