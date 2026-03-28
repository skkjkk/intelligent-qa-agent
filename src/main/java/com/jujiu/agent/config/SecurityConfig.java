package com.jujiu.agent.config;

import com.jujiu.agent.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 安全配置
 * 
 * 【设计目的】
 * 配置 JWT 认证过滤器链，定义哪些路径需要认证，哪些路径可以匿名访问
 * 
 * 【为什么要配置 Security？】
 * 1. 保护需要认证的接口（如对话接口）
 * 2. 放行公开接口（如登录、注册）
 * 3. JWT 过滤器验证 Token 合法性
 * 
 * @author 居九
 * @version 1.0.0
 * @since 2026/3/20 14:21
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置 CORS（跨域资源共享）
     * 
     * 【为什么要配置 CORS？】
     * 前后端分离项目中，前端和后端可能运行在不同的端口或域名下
     * 浏览器默认不允许跨域请求，需要后端明确允许
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有来源（生产环境建议限制为具体域名）
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        // 允许的 HTTP 方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许的请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 是否允许携带凭证（Cookie）
        configuration.setAllowCredentials(true);
        // 预检请求的缓存时间
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        log.info("[CORS] CORS 配置已加载，允许所有来源访问");
        return source;
    }

    /**
     * 配置 Spring Security 过滤器链，定义 HTTP 安全策略
     * 
     * 【设计目的】
     * 定义请求路径的安全策略：
     * - 哪些路径可以匿名访问
     * - 哪些路径需要认证
     * - JWT 过滤器放在 UsernamePasswordAuthenticationFilter 之前
     * 
     * @param http HttpSecurity 对象，用于配置 Web 安全设置
     * @return 配置完成的 SecurityFilterChain 对象
     * @throws Exception 配置过程中可能出现的异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("[SECURITY] 初始化 SecurityFilterChain 配置");
        
        // 配置安全策略
        http
                // 1. 配置 CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. 配置哪些路径允许匿名访问
                .authorizeHttpRequests(auth -> auth
                        // Swagger 相关接口放行
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "swagger-ui.html").permitAll()
                        // 认证相关接口放行（登录/注册/刷新）
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").permitAll()
                        // 工具接口放行（工具列表和执行都是公开的）
                        .requestMatchers("/api/v1/tools/list").permitAll()
                        .requestMatchers("/api/v1/tools/execute").authenticated()
                        // 放行错误页面，避免异步请求完成后被拦截
                        .requestMatchers("/error").permitAll()
                        // SSE 流式接口需要认证
                        .requestMatchers("/api/v1/chat/send/stream").authenticated()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )

                // 3. 禁用 CSRF（前后端分离项目通常不需要）
                .csrf(csrf -> csrf.disable())

                // 4. 配置 Session 策略（使用 JWT，不需要 Session）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4.1 配置 SecurityContext 不要求显式保存（解决异步请求问题）
                .securityContext(securityContext ->
                        securityContext.requireExplicitSave(false)
                )

                // 5. 配置异常处理，确保异步请求也能正确处理
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 检查是否是 SSE 请求
                            String accept = request.getHeader("Accept");
                            boolean isSseRequest = "/api/v1/chat/send/stream".equals(request.getRequestURI())
                                    || (accept != null && accept.contains("text/event-stream"));
                            if (isSseRequest) {
                                log.warn("[SECURITY][ACCESS_DENIED] SSE 请求被拒绝 - uri={}", request.getRequestURI());
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                return;
                            }
                            
                            log.error("[SECURITY][ACCESS_DENIED] 访问拒绝 - uri={}, error={}",
                                    request.getRequestURI(), authException.getMessage());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"message\":\"未授权访问\"}");
                        })

                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 检查是否是 SSE 请求
                            String accept = request.getHeader("Accept");
                            boolean isSseRequest = "/api/v1/chat/send/stream".equals(request.getRequestURI());
                            if (isSseRequest) {
                                log.warn("[SECURITY][ACCESS_DENIED] SSE 访问被拒绝 - uri={}", request.getRequestURI());
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                return;
                            }
                            
                            log.error("[SECURITY][ACCESS_DENIED] 访问被拒绝 - uri={}, error={}",
                                    request.getRequestURI(), accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":403,\"message\":\"访问被拒绝\"}");
                        })
                )

                // 6. 添加 JWT 认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
