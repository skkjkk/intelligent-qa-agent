package com.jujiu.agent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌提供者
 * <p>
 * 负责生成、解析和验证 JWT 令牌，提供基于 JWT 的身份认证功能。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *     <li>生成访问令牌（Access Token）和刷新令牌（Refresh Token）</li>
 *     <li>验证令牌的有效性和过期状态</li>
 *     <li>从令牌中提取用户信息（用户 ID、用户名、角色）</li>
 * </ul>
 * </p>
 * <p>
 * 使用说明：
 * <ul>
 *     <li>访问令牌有效期为 1 天，用于日常 API 请求认证</li>
 *     <li>刷新令牌有效期为 7 天，用于在访问令牌过期后获取新的访问令牌</li>
 *     <li>所有令牌都包含 userId、username 和 role 三个声明</li>
 * </ul>
 * </p>
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 17:53
 */
@Component
public class JwtTokenProvider {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * JWT 签名密钥
     */
    @Value("${jwt.secret}")
    private String secret;
    
    /**
     * 访问令牌过期时间：1 天
     * 适用于短期会话和用户操作
     */
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 24 * 60 * 60 * 1000L;
    
    /**
     * 刷新令牌过期时间：7 天
     * 用于在访问令牌过期后获取新的访问令牌，避免用户频繁重新登录
     */
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 获取 JWT 签名密钥
     * 使用 secret 字符串的 UTF-8 编码作为密钥
     * @return HMAC-SHA256 算法的密钥对象
     */
    private SecretKey getSecretKey() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
    
    /**
     * 生成 JWT 令牌
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param role 用户角色
     * @param expiration 令牌过期时间（毫秒）
     * @return 生成的 JWT 令牌字符串
     */
    private String generateToken(Long userId, String username, String role, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                // 令牌签发时间
                .issuedAt(now)
                // 令牌过期时间
                .expiration(expiry)
                // 用户 ID
                .claim("userId", String.valueOf(userId))
                // 用户名
                .claim("username", username)
                // 用户角色
                .claim("role", role)
                // 签名算法令牌签名，使用 HS256 算法
                .signWith(getSecretKey(), Jwts.SIG.HS256)
                // 生成 JWT 令牌字符串构建并返回令牌，
                .compact();
    }

    /**
     * 生成访问令牌
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param role 用户角色
     * @return 生成的访问令牌字符串
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, ACCESS_TOKEN_EXPIRATION_MS);
    }
    
    /**
     * 生成刷新令牌
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param role 用户角色
     * @return 生成的刷新令牌字符串
     */
    public String generateRefreshToken(Long userId, String username, String role) {
        return generateToken(userId, username, role, REFRESH_TOKEN_EXPIRATION_MS);
    }

    /**
     * 解析 JWT 令牌并获取声明信息
     *
     * @param token JWT 令牌字符串
     * @return 令牌中包含的声明信息
     */
    public Claims parseClaims(String token) {
        // 首先检查令牌是否为空
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 JWT 令牌是否有效
     *
     * @param token JWT 令牌字符串
     * @return 如果令牌有效返回 true，否则返回 false
     */
    public boolean validateToken(String token) {

        // 1. 检查是否在黑名单
        String blacklistKey = "token:blacklist:" + token;
        if (redisTemplate.hasKey(blacklistKey)) {
            // 已被登出
            return false;  
        }
        
        // 首先检查令牌是否为空
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // 2. 【新增】检查用户是否被全局拉黑（修改密码时用）
        try {
            Long userId = getUserId(token);
            String userBlacklistKey = "user:logout:" + userId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(userBlacklistKey))) {
                // 该用户已修改密码，所有token失效
                return false; 
            }
        } catch (Exception e) {
            return false;
        }
        
        try {
            Claims claims = parseClaims(token);
            // 检查过期时间（parseClaims 已经验证签名，这里额外检查是否过期）
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    /**
     * 从 JWT 令牌中获取用户 ID
     *
     * @param token JWT 令牌字符串
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String userId = parseClaims(token).get("userId", String.class);
        return Long.valueOf(userId);
    }
    
    /**
     * 从 JWT 令牌中获取用户名
     *
     * @param token JWT 令牌字符串
     * @return 用户名
     */
    public String getUsername(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return parseClaims(token).get("username", String.class);
    }

    /**
     * 从 JWT 令牌中获取用户角色
     *
     * @param token JWT 令牌字符串
     * @return 用户角色
     */
    public String getRole(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 获取访问令牌的过期时间（秒）
     *
     * @return 访问令牌过期时间的秒数
     */
    public long getAccessTokenExpiration(){
        return ACCESS_TOKEN_EXPIRATION_MS / 1000;
    }
}
