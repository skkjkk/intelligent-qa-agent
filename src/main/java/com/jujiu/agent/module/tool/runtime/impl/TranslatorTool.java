package com.jujiu.agent.module.tool.runtime.impl;

import com.jujiu.agent.module.tool.runtime.AbstractTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.InvalidUrlException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/26 16:45
 */
@Component
@Slf4j
public class TranslatorTool extends AbstractTool {
    private final RestTemplate restTemplate;

    @Value("${baidu.translate.app-id:}")
    private String appId;

    @Value("${baidu.translate.secret-key:}")
    private String secretKey;

    public TranslatorTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "translator";
    }


    @Override
    public String execute(Map<String, Object> params) {
        // 1. 获取参数
        String text = (String) params.get("text");
        String from = (String) params.get("from");
        String to = (String) params.get("to");

        // 2. 参数校验
        if (text == null || text.isBlank()) {
            return "错误：缺少必填参数 text（待翻译文本）";
        }
        // 3. 设置默认值
        if (from == null || from.isBlank()) {
            // 自动检测源语言
            from = "auto";
        }
        if (to == null || to.isBlank()) {
            // 默认翻译为中文
            to = "zh";
        }
        
        // 4. 检查 API Key
        if (appId == null || appId.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("[翻译] 未配置完整的 API Key");
            return "错误：缺少 API Key 配置";
        }
        
        try {
            // 5. 调用百度翻译 API
            return translateWithBaidu(text, from, to);
        } catch (Exception e) {
            log.error("[翻译] 翻译失败: {}", e.getMessage(), e);
            return "翻译失败：" + e.getMessage();
        }
    }

    private String translateWithBaidu(String text, String from, String to) {
        try {
            // 1. 生成随机数和签名
            String salt = String.valueOf(System.currentTimeMillis());
            String sign = generateSign(text, salt);
            
            // 2. 构建表单参数
            LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("q", text);
            formData.add("from", from);
            formData.add("to", to);
            formData.add("appid", appId);
            formData.add("salt", salt);
            formData.add("sign", sign);
            
            // 3. 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<LinkedMultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            log.info("[翻译] 开始调用百度翻译 API - from={}, to={}", from, to);

            // 4. 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://fanyi-api.baidu.com/api/trans/vip/translate", 
                    request, 
                    Map.class);

            if (response == null) {
                return "翻译失败：无响应";
            }
            // 5. 解析结果
            return parseTranslationResult(response);
            
        } catch (InvalidUrlException e) {
            log.error("[翻译] 无效的 URL: {}", e.getMessage());
            throw new RuntimeException("无效的 URL: " + e.getMessage());
        } catch (RestClientException | NoSuchAlgorithmException e) {
            log.error("[翻译] 调用失败: {}", e.getMessage());
            throw new RuntimeException("百度翻译 API 调用失败: " + e.getMessage());
        } 
    }

    private String parseTranslationResult(Map<String, Object> response) {
        try {
            // 1. 检查错误码
            Object errorCodeObj = response.get("error_code");
            String errorCode = null;
            if (errorCodeObj instanceof Integer) {
                errorCode = String.valueOf(errorCodeObj);
            } else if (errorCodeObj instanceof String) {
                errorCode = (String) errorCodeObj;
                
            }
            
            if (errorCode != null && !"0".equals(errorCode)) {
                String errorMsg = (String) response.get("error_msg");
                return "翻译失败：" + errorMsg;
            }
            
            // 2. 获取翻译结果
            @SuppressWarnings("unchecked")
            List<Map<String, String>> transResult = 
                    (List<Map<String, String>>) response.get("trans_result");

            if (transResult == null || transResult.isEmpty()) {
                return "翻译失败：无结果";
            }
            
            // 3. 提取翻译文本
            Map<String, String> result = transResult.get(0);
            String translatedText = result.get("dst");
            
            return "翻译结果：" + translatedText;
        } catch (Exception e) {
            log.error("[翻译] 解析结果失败: {}", e.getMessage());
            throw new RuntimeException("解析翻译结果失败: " + e.getMessage());
        }
    }

    private String generateSign(String text, String salt) throws NoSuchAlgorithmException {
        try {
            // 百度翻译签名算法：appId + text + salt +MD5(secretKey)
            String signStr = appId + text + salt + secretKey;
            
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(signStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }
}
