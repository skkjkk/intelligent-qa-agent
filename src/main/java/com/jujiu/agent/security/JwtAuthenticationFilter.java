package com.jujiu.agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


/**
 * JWT 认证过滤器
 * 
 * 从请求头中提取 JWT Token，验证有效性，并设置 Spring Security 上下文
 * 继承 OncePerRequestFilter 确保每个请求只执行一次
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 20:01
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    /**
     * 构造方法注入
     * 
     * @param jwtTokenProvider JWT 令牌提供者
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * 过滤请求，执行 JWT 认证
     *
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 从请求头中获取 JWT token
            String authorization = request.getHeader("Authorization");

            if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
                // 如果没有携带 token，则直接放行
                log.debug("未找到 Authorization 头，跳过认证");
                filterChain.doFilter(request, response);
                return;
            }

            // 2. 提取 token（去掉 "Bearer " 前缀）
            String token = authorization.substring(7);

            // 3. 验证 token 是否有效
            if (jwtTokenProvider.validateToken(token)) {
                // 4. 从 token 中解析用户信息
                String username = jwtTokenProvider.getUsername(token);
                String role = jwtTokenProvider.getRole(token);
                Long userId = jwtTokenProvider.getUserId(token);

                // 5. 创建认证对象（不需要密码，因为 token 已经验证过了）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                // principal（用户名）
                                username,
                                // credentials（JWT 认证不需要密码）
                                null,
                                // authorities（权限列表，可根据 role 动态添加）
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                // 6. 在请求中保存用户信息（方便后续使用）
                authentication.setDetails(userId);
                // 7. 将认证信息设置到 SecurityContext 中
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 认证成功 - 用户 ID: {}, 用户名：{}", userId, username);
            } else {
                log.warn("JWT Token 无效或已过期");
            }
        } catch (Exception e) {
            log.error("JWT 认证失败：{}", e.getMessage(), e);
            // 不在这里设置响应，让 Spring Security 的 ExceptionHandler 统一处理
        }
        // 8. 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}
