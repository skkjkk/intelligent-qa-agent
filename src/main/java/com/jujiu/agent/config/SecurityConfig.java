package com.jujiu.agent.config;

import com.jujiu.agent.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 14:21
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置 Spring Security 过滤器链，定义 HTTP 安全策略
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
                // 1. 配置哪些路径允许匿名访问
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").permitAll()
                        // 放行错误页面，避免异步请求完成后被拦截
                        .requestMatchers("/error").permitAll()
                        // SSE 流式接口需要认证
                        .requestMatchers("/api/v1/chat/send/stream").authenticated()
                        .anyRequest().authenticated()
                )
                // 2. 禁用 CSRF（前后端分离项目通常不需要）
                .csrf(csrf -> csrf.disable())
                // 3. 配置 Session 策略（使用 JWT，不需要 Session）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 3.1 配置 SecurityContext 不要求显式保存（解决异步请求问题）
                .securityContext(securityContext ->
                        securityContext.requireExplicitSave(false)
                )
                // 4. 配置异常处理，确保异步请求也能正确处理
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 检查是否是 SSE 请求
                            if ("text/event-stream".equals(response.getContentType())) {
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
                            if ("text/event-stream".equals(response.getContentType())) {
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
                // 5. 添加 JWT 认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
