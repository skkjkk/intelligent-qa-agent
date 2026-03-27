package com.jujiu.agent.tool.impl;

import com.jujiu.agent.model.dto.deepseek.ToolDefinition;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气查询工具
 *
 * 使用 OpenWeatherMap API
 * API文档：https://openweathermap.org/
 *
 * 调用流程：
 * 1. Geocoding API：将城市名转换为经纬度
 * 2. Weather API：用经纬度获取天气
 */
@Component
@Slf4j
public class WeatherTool extends AbstractTool {
    
    /**
     * RestTemplate 用于调用外部天气API
     */
    private final RestTemplate restTemplate;
    
    /**
     * 和风天气API Key
     * 从环境变量或配置文件读取
     */
    @Value("${weather.api.key:}")
    private String apiKey;

    /**
     * OpenWeatherMap API基础URL
     */
    @Value("${weather.api.url:}")
    private String apiUrl;
    
    /**
     * 构造函数
     */
    public WeatherTool(RestTemplate restTemplate, ToolRegistry toolRegistry) {
        this.restTemplate = restTemplate;
        // 将自己注册到注册中心
        toolRegistry.register(this);
    }

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的当前天气情况。" + 
                "参数：city（必填，城市名称，如'北京'、'上海'）。" + 
                "返回：天气状况、温度、湿度、风力等信息。";
    }

    @Override
    public String execute(Map<String, Object> params) {
        // 1. 获取城市参数
        String city = params.get("city") != null ? params.get("city").toString() : null;
        if (city == null || city.isEmpty()) {
            return "错误：缺少必填参数 city（城市名称）";
        }

        log.info("[天气查询] 城市 = {}, apiKey配置 = {}",
                city, apiKey != null && !apiKey.isEmpty() ? "已配置" : "未配置");
        
        // 2. 检查是否配置了有效的 apiKey 和 apiUrl
        if (apiKey == null || apiKey.isEmpty() || apiUrl == null || apiUrl.isEmpty()) {
            // 如果没有配置 API Key，返回模拟数据（方便开发测试）
            log.warn("[天气查询] 未配置完整的 API Key 或 URL，返回模拟数据");
            return getMockWeather(city);
        }
        try {
            // 3. 获取经纬度
            double[] coords = getCoordinates(city);
            if (coords == null) {
                return "无法获取城市坐标，请检查城市名称是否正确";
            }
            double lat = coords[0];
            double lon = coords[1];

            // 4. 获取天气数据
            return getWeatherByCoordinates(lat, lon, city);

        } catch (Exception e) {
            log.error("[天气查询] 调用失败: {}", e.getMessage(), e);
            return "天气查询失败：" + e.getMessage();
        }
    }

    @Override
    public ToolDefinition.Parameters getParameters() {
        // 1. 创建参数定义对象
        ToolDefinition.Parameters parameters = new ToolDefinition.Parameters();
        parameters.setType("object");
        
        // 2. 定义city参数
        ToolDefinition.Property property = new ToolDefinition.Property();
        property.setType("string");
        property.setDescription("城市名称，如'Beijing'、'Shanghai'");
        
        // 3. 将参数添加到properties
        Map<String, ToolDefinition.Property> properties = new HashMap<>();
        properties.put("city", property);
        parameters.setProperties(properties);
        
        // 4. 设置required
        List<String> require = new ArrayList<>();
        require.add("city");
        parameters.setRequired(require);
        
        return parameters;
    }
    
    /**
     * 获取城市坐标（使用 Geocoding API）
     */
    private double[] getCoordinates(String city) {
        try {
            // Geocoding API URL
            String url = String.format(
                    "http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=1&appid=%s",
                    city, apiKey
            );
            
            log.info("[天气查询] 获取坐标: {}", url);

            // 发送请求
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);

            if (response == null || response.isEmpty()) {
                log.error("[天气查询] 未找到城市: {}", city);
                return null;
            }
            
            Map<String, Object> location = response.get(0);
            Object latObj = location.get("lat");
            Object lonObj = location.get("lon");
            
            if (!(latObj instanceof Number) || !(lonObj instanceof Number)) {
                log.error("[天气查询] 坐标数据格式错误");
                return null;
            }

            double lat = ((Number) latObj).doubleValue();
            double lon = ((Number) lonObj).doubleValue();
            
            log.info("[天气查询] 城市: {} -> 坐标: lat={}, lon={}", city, lat, lon);
            return new double[]{lat, lon};
            
        } catch (Exception e) {
            log.error("[天气查询] 获取坐标失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据坐标获取天气（使用 Weather API）
     */
    private String getWeatherByCoordinates(double lat, double lon, String city) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://api.openweathermap.org/data/2.5/weather")
                    .queryParam("lat", lat)
                    .queryParam("lon", lon)
                    .queryParam("appid", apiKey)
                    .queryParam("units", "metric")
                    .queryParam("lang", "zh_cn")
                    .build()
                    .encode()
                    .toUri();

            log.info("[天气查询] 获取天气: {}", uri);

            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response == null) {
                return "天气查询失败，请稍后重试";
            }
            
            // 检查返回码
            Object codObj = response.get("cod");
            Integer cod = null;
            if (codObj instanceof Integer) {
                cod = (Integer) codObj;
            } else if (codObj instanceof Double) {
                cod = ((Double) codObj).intValue();
            }
            
            if (cod == null || cod != 200) {
                log.error("[天气查询] API返回错误: {}", response.get("message"));
                return "天气查询失败：" + response.get("message");
            }

            // 解析天气数据
            String cityName = (String) response.get("name");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> weatherList = (List<Map<String, Object>>) response.get("weather");
            String weatherDesc = weatherList != null && !weatherList.isEmpty()
                    ? weatherList.get(0).get("description").toString() : "未知";

            @SuppressWarnings("unchecked")
            Map<String, Object> main = (Map<String, Object>) response.get("main");
            String temp = main != null ? main.get("temp").toString() : "未知";
            String feelsLike = main != null ? main.get("feels_like").toString() : "未知";
            String humidity = main != null ? main.get("humidity").toString() : "未知";

            @SuppressWarnings("unchecked")
            Map<String, Object> wind = (Map<String, Object>) response.get("wind");
            String windSpeed = wind != null ? wind.get("speed").toString() : "未知";

            // 格式化返回结果
            return String.format(
                    "%s当前天气：%s，温度%s℃，体感温度%s℃，湿度%s%%，风速%sm/s",
                    cityName != null ? cityName : city,
                    weatherDesc,
                    temp,
                    feelsLike,
                    humidity,
                    windSpeed
            );
            
        } catch (Exception e) {
            log.error("[天气查询] 获取天气失败: {}", e.getMessage(), e);
            return "天气查询失败：" + e.getMessage();
        }
        
    }
        
    /**
     * 返回模拟天气数据
     * 用于开发测试阶段，没有真实API Key时
     */
    private String getMockWeather(String city) {
        log.warn("[天气查询] 未配置 API Key，返回模拟数据");
        return city + "当前天气：晴，温度22℃，湿度50%，东南风2级（模拟数据）";
    }

    @SuppressWarnings("unchecked")
    private <T> T castSafely(Object obj, Class<T> type) {
        if (obj == null) {
            return null;
        }
        if (type.isInstance(obj)) {
            return (T) obj;
        }
        return null;
    }
}
