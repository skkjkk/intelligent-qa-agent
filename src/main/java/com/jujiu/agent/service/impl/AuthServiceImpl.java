package com.jujiu.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.request.*;
import com.jujiu.agent.model.dto.response.LoginResponse;
import com.jujiu.agent.model.dto.response.UserInfoResponse;
import com.jujiu.agent.model.entity.LoginLog;
import com.jujiu.agent.model.entity.User;
import com.jujiu.agent.repository.LoginLogRepository;
import com.jujiu.agent.repository.UserRepository;
import com.jujiu.agent.security.JwtTokenProvider;
import com.jujiu.agent.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 15:44
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    private LoginLogRepository loginLogRepository;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    // 使用 BCryptPasswordEncoder 替代直接的 BCrypt 工具类
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String failKey = "login:fail:" + loginRequest.getUsername();
        
        // 检查是否被锁定
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        failCountStr = failCountStr == null ? "0" : failCountStr;
        if (Integer.parseInt(failCountStr) >= 5) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        
        // 使用 LambdaQueryWrapper 查询用户（类型安全）
        User user = userRepository.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginRequest.getUsername())
        );
        
        if (user == null) {
            // 用户不存在，也算一次失败
            recordFailAttempt(failKey);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        // 验证密码（使用 Spring Security 的 BCryptPasswordEncoder）
        if (!passwordEncoder.matches(loginRequest.getPassword().trim(), user.getPassword())) {
            // 密码错误，记录失败
            recordFailAttempt(failKey);
            // 密码错误，保存登录日志
            saveLoginLog(user.getId(), 0);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        
        // 密码验证通过后，清除该用户的黑名单（允许新Token生效）
        String userBlacklistKey = "user:logout:" + user.getId();
        redisTemplate.delete(userBlacklistKey);
        
        // 登录成功，保存登录日志
        saveLoginLog(user.getId(), 1);
        // 登录成功，清除失败记录
        redisTemplate.delete(failKey);
        
        // 生成访问令牌
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), 
                user.getUsername(), 
                user.getRole()
        );
        
        // 生成刷新令牌
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), 
                user.getUsername(), 
                user.getRole()
        );
        
        // 返回登录响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    @Override
    public LoginResponse register(RegisterRequest registerRequest) {
        // 1. 检查用户名是否已存在
        User existUser = userRepository.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, registerRequest.getUsername())
        );
        if (existUser != null) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // 2. 检查邮箱是否已注册
        User existEmail = userRepository.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, registerRequest.getEmail())
        );
        if (existEmail != null) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }

        // 3. 密码加密（使用 Spring Security 的 BCryptPasswordEncoder）
        String hashedPassword = passwordEncoder.encode(registerRequest.getPassword());
        
        // 4. 创建新用户
        User newUser = User.builder()
                .username(registerRequest.getUsername())
                .password(hashedPassword)
                .email(registerRequest.getEmail())
                .nickname(registerRequest.getNickname())
                // 默认角色
                .role("USER") 
                .status(1)
                .build();

        // 5. 插入数据库
        userRepository.insert(newUser);

        // 6. 注册成功后自动登录，生成 Token
        String accessToken = jwtTokenProvider.generateAccessToken(
                newUser.getId(), 
                newUser.getUsername(), 
                newUser.getRole()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                newUser.getId(), 
                newUser.getUsername(), 
                newUser.getRole()
        );

        // 7. 返回登录响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    private void saveLoginLog(Long userId, int status) {
        // 登录日志
        LoginLog log = LoginLog.builder()
                // 未知用户用 -1 标记
                .userId(userId)
                .ip(getClientIp())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                // 登录状态：0-失败，1-成功
                .status(status)
                .loginTime(LocalDateTime.now())
                .build();
        loginLogRepository.insert(log);
    }
    
    /**
     * 获取客户端 IP 地址
     * 
     * @return 客户端 IP 地址
     */
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    private void recordFailAttempt(String failKey) {
        // 次数 +1，返回增加后的值
        Long count = redisTemplate.opsForValue().increment(failKey);

        // 如果是第一次设置（count=1），设置 10 分钟过期
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, 10, TimeUnit.MINUTES);
        }
    }

    /**
     * 刷新访问令牌
     * 使用刷新令牌获取新的访问令牌和刷新令牌
     *
     * @param refreshRequest 刷新令牌请求，包含旧的刷新令牌
     * @return 包含新访问令牌和新刷新令牌的响应
     */
    @Override
    public LoginResponse refresh(RefreshRequest refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();
        // 1. 验证刷新令牌的有效性
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        } 
        
        // 2. 从刷新令牌中提取用户信息
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        // 3. 生成新的 Access Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, role);

        // 4. 返回新的访问令牌和刷新令牌
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    @Override
    public void logout(LogoutRequest logoutRequest) {
        String logoutRequestToken = logoutRequest.getToken();
        log.info("Logout request received for token: {}", logoutRequestToken);
        
        // 验证刷新令牌的有效性
        if (!jwtTokenProvider.validateToken(logoutRequestToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Claims claims = jwtTokenProvider.parseClaims(logoutRequestToken);
        Date expiration = claims.getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();
        log.info("Token expiration time: {} ms", remainingTime);
        
        if (remainingTime <= 0) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }
        
        // 将令牌加入黑名单，过期时间与令牌剩余有效时间一致
        String blacklistKey = "token:blacklist:" + logoutRequestToken;
        redisTemplate.opsForValue().set(blacklistKey, "1", remainingTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public UserInfoResponse getCurrentUser(Long userId) {
        log.info("Getting current user for userId: {}", userId);
        
        // 1. 根据 userId 获取用户信息
        User user = userRepository.selectById(userId);
        if (user == null) {
            // 用户不存在
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        // 2. 构建用户信息响应
        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        log.info("Changing password for user: {}", userId);
        
        // 1. 获取用户信息
        User user = userRepository.selectById(userId);
        
        // 2. 验证旧密码与数据库密码是否一致
        if (!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_WRONG);
        }
        
        // 3. 更新用户密码
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.updateById(user);
        
        log.info("Password changed for user: {}", user.getUsername());


        // 4. 将该用户加入黑名单（使该用户所有token失效）
        String userBlacklistKey = "user:logout:" + userId;
        redisTemplate.opsForValue().set(userBlacklistKey, "1", 7, TimeUnit.DAYS);
    }
}
