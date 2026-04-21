package com.jujiu.agent.module.chat.infrastructure.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jujiu.agent.shared.constant.BusinessConstants;
import com.jujiu.agent.shared.exception.BusinessException;
import com.jujiu.agent.shared.result.ResultCode;
import com.jujiu.agent.module.chat.infrastructure.config.DeepSeekProperties;
import com.jujiu.agent.infrastructure.config.WebClientConfig;
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
                completionTokens,
                null
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
     * 带工具的对话
     * 向 DeepSeek API 发送支持 Function Calling 的请求，允许 AI 调用指定的工具来完成复杂任务。
     * 该方法会阻塞等待 API 完整响应，返回最终结果和 Token 使用统计。
     *
     * @param messages 对话消息列表，包含完整的对话历史上下文
     * @param tools 工具定义列表，指定 AI 可调用的所有工具及其参数格式
     * @return DeepSeekResult 对话结果，包含 AI 回复内容、Token 统计信息和工具调用信息
     * @throws BusinessException 当 API 返回空响应或格式异常时抛出
     */
    public DeepSeekResult chatWithTools(List<DeepSeekMessage> messages, List<ToolDefinition> tools) {
        log.info("[DEEPSEEK][CHAT_WITH_TOOLS] 开始对话 - messageCount={}, toolCount={}",
                messages.size(), tools.size());
        
        // 构建 DeepSeek API 请求参数
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(deepSeekProperties.getModel());
        request.setTools(tools);
        request.setMessages(messages);
        request.setTemperature(deepSeekProperties.getTemperature());

        log.debug("[DEEPSEEK][CHAT_WITH_TOOLS] 请求参数构建完成 - messageCount={}, toolCount={}",
                messages.size(), tools.size());
        
        // 构建 HTTP 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekProperties.getApiKey());
        
        // 封装请求体和请求头
        HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(request, headers);
        
        // 发送 POST 请求到 DeepSeek API
        log.info("[DEEPSEEK][CHAT_WITH_TOOLS] 发送 POST 请求 - url={}, entity={}", 
                deepSeekProperties.getBaseUrl() + "/chat/completions", entity.getBody());
        long startTime = System.currentTimeMillis();

        DeepSeekResponse response = restTemplate.postForObject(
                deepSeekProperties.getBaseUrl() + "/chat/completions",
                entity,
                DeepSeekResponse.class
        );
        
        long costTime = System.currentTimeMillis() - startTime;
        log.info("[DEEPSEEK][CHAT_WITH_TOOLS] DeepSeek API 请求完成 - costTime={}ms, response={}",
                costTime, response != null ? "not null" : "null");
        
        // 检查 API 响应是否为空，防止 NullPointerException
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.error("[DEEPSEEK][CHAT_WITH_TOOLS] DeepSeek API 响应为空");
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
         }
        
        // 获取第一个选择（DeepSeek 默认只返回一个）
        DeepSeekResponse.Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            log.error("[DEEPSEEK][PARSE_ERROR] API 返回的消息格式异常 - choice={}, message={},",
                    choice, choice != null ? choice.getMessage() : null);
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_FORMAT_ERROR);
        }
        
        log.debug("[DEEPSEEK][CHAT_WITH_TOOLS] DeepSeek 工具调用成功 - tools={}, response={}", tools, response);
        
        // 提取 Token 使用统计信息
        DeepSeekResponse.Usage usage = response.getUsage();
        int totalTokens = usage != null ? usage.getTotalTokens() : 0;
        int promptTokens = usage != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage != null ? usage.getCompletionTokens() : 0;
        
        log.debug("[DEEPSEEK][CHAT_WITH_TOOLS] DeepSeek API TOKEN 使用情况 - totalTokens={}, promptTokens={}, completionTokens={}",
                totalTokens, promptTokens, completionTokens);

        // 构建并返回结果对象
        return new DeepSeekResult(
                choice.getMessage().getContent(),
                totalTokens,
                promptTokens,
                completionTokens,
                choice.getMessage().getToolCalls()
        );
    }

    /**
     * 带工具的流式对话
     * 向 DeepSeek API 发送支持 Function Calling 的流式请求，返回包含内容增量和工具调用增量的响应流。
     * 使用 SSE（Server-Sent Events）协议接收实时数据，自动处理 [DONE] 结束标记和 usage 统计信息。
     *
     * @param messages 对话消息列表，包含完整的对话历史上下文
     * @param tools 工具定义列表，指定 AI 可调用的所有工具及其参数格式
     * @return Flux<ToolStreamChunk> 流式响应，每个 chunk 包含内容增量、工具调用增量或结束信息
     */
    public Flux<ToolStreamChunk> chatStreamWithTools(
            List<DeepSeekMessage> messages,
            List<ToolDefinition> tools) {
    
        // 构建 DeepSeek API 请求体
        DeepSeekRequest request = new DeepSeekRequest();
        request.setMessages(messages);
        request.setModel(deepSeekProperties.getModel());
        request.setTools(tools);
        request.setStream(true);
        request.setTemperature(deepSeekProperties.getTemperature());
    
        // 打印详细的请求日志，包括所有消息的内容预览
        log.info("[DEEPSEEK][STREAM_WITH_TOOLS] 发送请求 - messageCount={}, toolCount={}", messages.size(), tools.size());
        for (int i = 0; i < messages.size(); i++) {
            DeepSeekMessage msg = messages.get(i);
            String contentPreview = msg.getContent() != null ? (msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "..." : msg.getContent()) : "null";
            log.debug("[DEEPSEEK][STREAM_WITH_TOOLS] Message[{}] - role={}, content={}, hasToolCalls={}, toolCallId={}",
                i, msg.getRole(), contentPreview,
                msg.getToolCalls() != null && !msg.getToolCalls().isEmpty(),
                msg.getToolCallId());
        }
    
        // 使用 WebClient 发送 POST 请求并获取流式响应
        return webClientConfig.webClientBuilder().build()
                .post()
                .uri(deepSeekProperties.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                // 过滤空行
                .filter(line -> line != null && !line.isEmpty())
                .map(line -> {
                    try {
                        // 去掉 "data: " 前缀，提取纯 JSON 数据
                        String data = line.startsWith("data: ") ? line.substring(6) : line;
    
                        // 检查是否为 [DONE] 结束标记
                        if (BusinessConstants.SSE_DONE.equals(data)) {
                            // 如果是 [DONE]，返回结束块
                            return ToolStreamChunk.end("stop", null);
                        }
    
                        // 解析 JSON 为 StreamResponse 对象
                        StreamResponse response = objectMapper.readValue(data, StreamResponse.class);
                        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                            // 解析失败或结构异常，返回空内容块
                            return ToolStreamChunk.delta(null, null, null);
                        }
    
                        // 获取第一个 choice（DeepSeek 默认只返回一个）
                        StreamResponse.StreamChoice choice = response.getChoices().get(0);
                        if (choice == null) {
                            return ToolStreamChunk.delta(null, null, null);
                        }
    
                        // 获取 delta 增量对象
                        StreamResponse.StreamDelta delta = choice.getDelta();
    
                        String content = null;
                        List<StreamResponse.StreamToolCallDelta> toolCalls = null;
    
                        if (delta != null) {
                            content = delta.getContent();
                            toolCalls = delta.getToolCalls();
                        }
    
                        if (response.getUsage() != null) {
                            // 如果有 usage 统计信息，返回结束块
                            return ToolStreamChunk.end(choice.getFinishReason(), response.getUsage());
                        }
    
                        // 返回包含内容和工具调用的增量块
                        return ToolStreamChunk.delta(content, toolCalls, choice.getFinishReason());
    
                    } catch (Exception e) {
                        log.error("[DEEPSEEK][STREAM_WITH_TOOLS_PARSE_ERROR] 流式工具响应解析失败 - line={}", line, e);
                        return ToolStreamChunk.delta(null, null, null);
                    }
                })
                // 过滤掉无意义的空块，只保留有实际内容、工具调用或结束的块
                .filter(chunk -> 
                        chunk != null && (chunk.isEnd() 
                                || (chunk.getContent() != null && !chunk.getContent().isEmpty()) 
                                || (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) 
                        )
                );
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
            
            // 工具调用
            @JsonProperty("tool_calls")
            private List<StreamToolCallDelta> toolCalls;
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

        /**
         * 工具调用增量内部类
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StreamToolCallDelta {
            
            // 工具调用索引
            private Integer index;

            // 工具调用ID
            private String id;

            // 工具调用类型
            private String type;

            // 工具调用函数
            private StreamFunctionDelta function;
        }
        
        /**
         * 函数调用增量内部类
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StreamFunctionDelta{
            // 函数名称
            private String name;
            
            // 函数参数
            private String arguments;
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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolStreamChunk{
        // 普通文本增量
        private String content;

        // 工具调用增量
        private List<StreamResponse.StreamToolCallDelta> toolCalls;

        // 本次返回的结束原因
        private String finishReason;

        // token 统计，只会在结束块里有值
        private StreamResponse.StreamUsage usage;

        // 是否为结束块
        private boolean end;

        public static ToolStreamChunk delta(String content, 
                                            List<StreamResponse.StreamToolCallDelta> toolCalls, 
                                            String finishReason) {
            return new ToolStreamChunk(content, toolCalls, finishReason, null, false);
        }
        
        public static ToolStreamChunk end(String finishReason, 
                                          StreamResponse.StreamUsage usage) {
            return new ToolStreamChunk(null, null, finishReason, usage, true);
        }
    }
}
