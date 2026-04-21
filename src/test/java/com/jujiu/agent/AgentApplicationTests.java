package com.jujiu.agent;

import com.jujiu.agent.module.tool.application.registry.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * 应用启动测试
 */
@SpringBootTest(properties = {
        "AMAP_WEATHER_KEY=test-key",
        "amap.weather.key=test-key"
})
class AgentApplicationTests {

    @Test
    void contextLoads() {
        // 测试Spring上下文是否正常加载
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        ToolRegistry toolRegistry() {
            return mock(ToolRegistry.class);
        }
    }
}
