package com.jujiu.agent.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger OpenAPI 配置
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 21:30
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "智能问答助手 API",
        description = "智能问答助手系统接口文档",
        version = "v1.0.0"
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "本地环境")
    },
    security = {
        @SecurityRequirement(name = "Bearer Authentication")
    }
)
@SecurityScheme(
    name = "Bearer Authentication",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "JWT 令牌认证，请输入 token 本身（会自动添加 Bearer 前缀）"
)
public class SwaggerConfig {
}
