package com.jujiu.agent.module.tool.runtime.impl;

import com.jujiu.agent.module.tool.runtime.AbstractTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/26 15:35
 */
@Component
@Slf4j
public class WebSearchTool extends AbstractTool {

    private final RestTemplate restTemplate;

    // SerpAPI API密钥
    @Value("${serpapi.api.key:}")
    private  String apiKey;

    public WebSearchTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    @Override
    public String getName() {
        return "web_search";
    }
    
    /**
     * 执行工具
     * 【参数验证】
     * 子类应该在 execute() 中验证必填参数，返回错误信息而不是抛异常
     *
     * @param params 工具参数（如 {"city": "北京"}）
     * @return 执行结果（成功或错误信息）
     */
    @Override
    public String execute(Map<String, Object> params) {
        // 1. 获取搜索关键词
        String query = (String) params.get("query");
        log.info("[网页搜索] 收到搜索请求");
        
        // 2. 参数校验
        if (query == null || query.isEmpty()) {
            return "错误：缺少必填参数 query（搜索关键词）";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[网页搜索] 未配置完整的 API Key，返回模拟数据");
            return "错误：缺少必填参数 apiKey（SerpAPI API密钥）";
        }
        
        try {
            // 3. 调用serpapi进行搜索
            return searchWithSerpAPI(query);
            
        } catch (Exception e) {
            log.error("[网页搜索] 搜索失败: {}", e.getMessage(), e);
            return "搜索失败：" + e.getMessage();
        }
    }

    private String searchWithSerpAPI(String query) throws UnsupportedEncodingException {
        try {
            // 1. 构建请求URL
            URI uri = UriComponentsBuilder
                    .fromUriString("https://serpapi.com/search")
                    .queryParam("q", query)
                    .queryParam("engine", "google")
                    .queryParam("api_key", apiKey)
                    .build()
                    .encode()
                    .toUri();
            
            // 2. 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response == null) {
                return "错误：搜索无结果";
            }
            
            // 3. 解析搜索结果
            return parseSearchResult(response);
            
        } catch (Exception e) {
            log.error("[网页搜索] SerpAPI 调用失败: {}", e.getMessage());
            throw new RuntimeException("SerpAPI 调用失败: " + e.getMessage());
        }
    }

    private String parseSearchResult(Map<String, Object> response) {

        try {
            // 1. 获取搜索结果数组
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("organic_results");
            
            if (results == null || results.isEmpty()) {
                return "未找到相关搜索结果";
            }
            
            // 2. 格式化前 5 个结果
            StringBuilder sb = getStringBuilder(results);

            return sb.toString();
        } catch (Exception e) {
            log.error("[网页搜索] 解析搜索结果失败: {}", e.getMessage());
            throw new RuntimeException("解析搜索结果失败: " + e.getMessage());
        }
    }

    private static StringBuilder getStringBuilder(List<Map<String, Object>> results) {
        // 1. 创建StringBuilder对象
        StringBuilder sb = new StringBuilder("搜索结果：\n");
        int count = Math.min(results.size(), 5);

        // 2. 遍历结果集
        for (int i = 0; i < count; i++) {
            Map<String, Object> result = results.get(i);
            String title = (String) result.getOrDefault("title", "无标题");
            String snippet = (String) result.getOrDefault("snippet", "无摘要");
            String link = (String) result.getOrDefault("link", "无链接");
            
            // 3. 格式化结果
            sb.append(String.format("%d, %s\n摘要：%s\n链接：%s\n\n",
                    i + 1, title, snippet, link));
        }
        return sb;
    }
}
