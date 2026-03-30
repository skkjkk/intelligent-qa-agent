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
     * @param request     HTTP 请求对象
     * @param response    HTTP 响应对象
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 检查是否为异步 dispatch，如果是则直接放行
        if (request.isAsyncStarted()) {
            log.debug("[SECURITY][FILTER] 检测到异步请求，直接放行 - uri={}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }
        
        long startTime = System.currentTimeMillis();
        log.info("[SECURITY][FILTER] 开始处理请求 - method={}, uri={}", request.getMethod(), request.getRequestURI());

        try {
            // 1. 从请求头中获取 JWT token
            String authorization = request.getHeader("Authorization");

            if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
                // 如果没有携带 token，则直接放行
                log.debug("[SECURITY][FILTER] 未找到 Authorization 头，跳过认证 - uri={}", request.getRequestURI());
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

                log.info("[SECURITY][AUTH_SUCCESS] JWT 认证成功 - userId={}, username={}, role={}, uri={}, costTime={}ms",
                        userId, username, role, request.getRequestURI(), System.currentTimeMillis() - startTime);
            } else {
                log.warn("[SECURITY][AUTH_FAILED] Token 无效或已过期 - uri={}", request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("[SECURITY][AUTH_ERROR] JWT 认证异常 - uri={}, error={}, costTime={}ms",
                    request.getRequestURI(), e.getMessage(), System.currentTimeMillis() - startTime, e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + e.getMessage() + "\"}");
            return;
        } finally {
            log.info("[SECURITY][FILTER] 请求处理完成 - uri={}, totalCostTime={}ms",
                    request.getRequestURI(), System.currentTimeMillis() - startTime);
        }
        // 8. 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}
