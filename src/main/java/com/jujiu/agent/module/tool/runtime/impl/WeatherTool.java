package com.jujiu.agent.module.tool.runtime.impl;

import com.jujiu.agent.module.tool.runtime.AbstractTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 天气查询工具
 *
 * 使用高德天气 API。
 * API 文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo
 */
@Component
@Slf4j
public class WeatherTool extends AbstractTool {

    private final RestTemplate restTemplate;

    @Value("${amap.weather.key:}")
    private String apiKey;

    @Value("${amap.weather.url:}")
    private String apiUrl;

    public WeatherTool(RestTemplate restTemplate) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    
    }

    @Override
    public String getName() {
        return "weather";
    }
    

    @Override
    public String execute(Map<String, Object> params) {
        String city = params.get("city") != null ? params.get("city").toString() : null;
        if (city == null || city.isEmpty()) {
            return "错误：缺少必填参数 city（城市名称或 adcode）";
        }

        log.info("[天气查询] 收到天气查询请求");

        if (apiKey == null || apiKey.isEmpty() || apiUrl == null || apiUrl.isEmpty()) {
            log.warn("[天气查询] 未配置完整的 API Key 或 URL，返回模拟数据");
            return getMockWeather(city);
        }

        try {
            return getWeatherByCity(city);
        } catch (Exception e) {
            log.error("[天气查询] 调用失败: {}", e.getMessage(), e);
            return "天气查询失败：" + e.getMessage();
        }
    }

    private String getWeatherByCity(String city) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(apiUrl)
                    .queryParam("city", city)
                    .queryParam("key", apiKey)
                    .queryParam("extensions", "base")
                    .queryParam("output", "JSON")
                    .build()
                    .encode()
                    .toUri();

            log.info("[天气查询] 请求高德天气: {}", uri);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);

            if (response == null) {
                return "天气查询失败：高德返回为空";
            }

            Object status = response.get("status");
            if (!"1".equals(String.valueOf(status))) {
                return "天气查询失败：" + response.get("info");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lives = (List<Map<String, Object>>) response.get("lives");
            if (lives == null || lives.isEmpty()) {
                return "天气查询失败：未返回实时天气数据";
            }

            Map<String, Object> live = lives.get(0);
            String province = String.valueOf(live.getOrDefault("province", ""));
            String cityName = String.valueOf(live.getOrDefault("city", city));
            String weather = String.valueOf(live.getOrDefault("weather", "未知"));
            String temperature = String.valueOf(live.getOrDefault("temperature", "未知"));
            String windDirection = String.valueOf(live.getOrDefault("winddirection", "未知"));
            String windPower = String.valueOf(live.getOrDefault("windpower", "未知"));
            String humidity = String.valueOf(live.getOrDefault("humidity", "未知"));
            String reportTime = String.valueOf(live.getOrDefault("reporttime", ""));

            return String.format(
                    "%s%s当前天气：%s，温度%s℃，湿度%s%%，风向%s，风力%s级，发布时间%s",
                    province,
                    cityName,
                    weather,
                    temperature,
                    humidity,
                    windDirection,
                    windPower,
                    reportTime
            );
        } catch (Exception e) {
            log.error("[天气查询] 高德天气请求失败: {}", e.getMessage(), e);
            return "天气查询失败：" + e.getMessage();
        }
    }

    private String getMockWeather(String city) {
        log.warn("[天气查询] 未配置 API Key，返回模拟数据");
        return city + "当前天气：晴，温度22℃，湿度50%，东南风2级（模拟数据）";
    }
}
