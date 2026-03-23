package com.jujiu.agent.client;

import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.config.DeepSeekProperties;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import com.jujiu.agent.model.dto.deepseek.DeepSeekRequest;
import com.jujiu.agent.model.dto.deepseek.DeepSeekResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    /**
     * 与 DeepSeek AI 进行对话
     *
     * @param messages 对话消息列表
     * @return AI 回复的内容
     */
    public String chat(List<DeepSeekMessage> messages) {
        // 1. 构建请求参数
        DeepSeekRequest deepSeekRequest = new DeepSeekRequest();
        deepSeekRequest.setModel(deepSeekProperties.getModel());
        deepSeekRequest.setMessages(messages);
            
        // 2. 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekProperties.getApiKey());
            
        // 3. 打包请求头 + 请求体
        HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(deepSeekRequest, headers);
            
        // 4. 发送 POST 请求
        DeepSeekResponse response = restTemplate.postForObject(
                // 请求地址（baseUrl 拼上路径）
                deepSeekProperties.getBaseUrl() + "/chat/completions",
                // 请求体
                entity,
                // 响应类型
                DeepSeekResponse.class
        );
            
        // 5. 空值检查，防止 NullPointerException
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.error("DeepSeek API 返回空响应");
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_NULL);
        }
            
        // 6. 获取第一个选择 getChoices() → 取 choices 列表 .get(0) → 取第一个（DeepSeek 默认只返回一个）
        DeepSeekResponse.Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            log.error("DeepSeek API 返回的消息格式异常");
            throw new BusinessException(ResultCode.DEEPSEEK_API_RETURN_FORMAT_ERROR);
        }
            
        // 7. 返回 AI 回复内容.getMessage() → 取消息对象  .getContent() → 取文本内容
        return choice.getMessage().getContent();
    }
}
