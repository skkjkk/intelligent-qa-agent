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
        log.info("[AUTH][LOGIN] 收到登录请求 - username={}, ip={}", 
                loginRequest.getUsername(), getClientIp());
        
        String failKey = "login:fail:" + loginRequest.getUsername();
        
        // 检查是否被锁定
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        failCountStr = failCountStr == null ? "0" : failCountStr;
        if (Integer.parseInt(failCountStr) >= 5) {
            log.warn("[AUTH][LOGIN] 登录失败次数过多，账号已被临时锁定 - username={}, failCount={}", 
                    loginRequest.getUsername(), failCountStr);
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        
        // 使用 LambdaQueryWrapper 查询用户（类型安全）
        User user = userRepository.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginRequest.getUsername())
        );
        
        if (user == null) {
            log.warn("[AUTH][LOGIN] 用户不存在 - username={}, ip={}", 
                    loginRequest.getUsername(), getClientIp());
            // 用户不存在，也算一次失败
            recordFailAttempt(failKey);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        // 验证密码（使用 Spring Security 的 BCryptPasswordEncoder）
        if (!passwordEncoder.matches(loginRequest.getPassword().trim(), user.getPassword())) {
            log.warn("[AUTH][LOGIN] 密码错误 - userId={}, username={}, ip={}", 
                    user.getId(), user.getUsername(), getClientIp());
            // 密码错误，记录失败
            recordFailAttempt(failKey);
            // 密码错误，保存登录日志
            saveLoginLog(user.getId(), 0);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        
        // 密码验证通过后，清除该用户的黑名单（允许新 Token 生效）
        String userBlacklistKey = "user:logout:" + user.getId();
        redisTemplate.delete(userBlacklistKey);
                
        // 登录成功，保存登录日志
        saveLoginLog(user.getId(), 1);
        // 登录成功，清除失败记录
        redisTemplate.delete(failKey);
                
        log.info("[AUTH][LOGIN_SUCCESS] 登录成功 - userId={}, username={}, ip={}", 
                user.getId(), user.getUsername(), getClientIp());
        
        log.debug("[AUTH][TOKEN_GEN] 开始生成 Access Token 和 Refresh Token - userId={}, username={}", 
                user.getId(), user.getUsername());
        
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
        
        log.info("[AUTH][LOGIN_COMPLETE] 登录流程完成 - userId={}, username={}, tokenType=Bearer, expiresIn={}s", 
                user.getId(), user.getUsername(), jwtTokenProvider.getAccessTokenExpiration());
        
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
        log.info("[AUTH][REGISTER] 收到注册请求 - username={}, email={}, nickname={}", 
                registerRequest.getUsername(), registerRequest.getEmail(), registerRequest.getNickname());
        
        // 1. 检查用户名是否已存在
        User existUser = userRepository.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, registerRequest.getUsername())
        );
        if (existUser != null) {
            log.warn("[AUTH][REGISTER] 用户名已被注册 - username={}", registerRequest.getUsername());
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // 2. 检查邮箱是否已注册
        User existEmail = userRepository.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, registerRequest.getEmail())
        );
        if (existEmail != null) {
            log.warn("[AUTH][REGISTER] 邮箱已被注册 - email={}", registerRequest.getEmail());
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
        log.info("[AUTH][REGISTER_COMPLETE] 注册流程完成 - userId={}, username={}, email={}", 
                newUser.getId(), newUser.getUsername(), newUser.getEmail());
        
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
        log.info("[AUTH][REFRESH_TOKEN] 收到 Token 刷新请求");
        String refreshToken = refreshRequest.getRefreshToken();
        
        // 1. 验证刷新令牌的有效性
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("[AUTH][REFRESH_TOKEN] 刷新令牌无效");
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        } 
        
        // 2. 从刷新令牌中提取用户信息
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        // 3. 生成新的 Access Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, role);
        
        log.info("[AUTH][REFRESH_TOKEN_SUCCESS] Token 刷新成功 - userId={}, username={}, role={}", 
                userId, username, role);

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
        log.info("[AUTH][LOGOUT] 收到退出请求 - tokenPrefix={}", 
                logoutRequest.getToken() != null && logoutRequest.getToken().length() > 10 
                        ? logoutRequest.getToken().substring(0, 10) + "..." : "null");
        String logoutRequestToken = logoutRequest.getToken();
        
        // 验证刷新令牌的有效性
        if (!jwtTokenProvider.validateToken(logoutRequestToken)) {
            log.warn("[AUTH][LOGOUT] 退出令牌无效");
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Claims claims = jwtTokenProvider.parseClaims(logoutRequestToken);
        Date expiration = claims.getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();
        log.info("[AUTH][LOGOUT] Token 剩余有效期 - remainingTime={}ms, expiration={}", 
                remainingTime, expiration);
        
        if (remainingTime <= 0) {
            log.warn("[AUTH][LOGOUT] Token 已过期，无法执行退出操作");
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }
        
        // 将令牌加入黑名单，过期时间与令牌剩余有效时间一致
        String blacklistKey = "token:blacklist:" + logoutRequestToken;
        redisTemplate.opsForValue().set(blacklistKey, "1", remainingTime, TimeUnit.MILLISECONDS);
        
        Long userId = jwtTokenProvider.getUserId(logoutRequestToken);
        log.info("[AUTH][LOGOUT_SUCCESS] 退出成功 - userId={}, token 已加入黑名单，blacklistExpire={}ms", 
                userId, remainingTime);
    }

    @Override
    public UserInfoResponse getCurrentUser(Long userId) {
        log.info("[AUTH][GET_USER_INFO] 请求获取用户信息 - userId={}", userId);
        
        // 1. 根据 userId 获取用户信息
        User user = userRepository.selectById(userId);
        if (user == null) {
            log.warn("[AUTH][GET_USER_INFO] 用户不存在 - userId={}", userId);
            // 用户不存在
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        // 2. 构建用户信息响应
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
        
        log.info("[AUTH][GET_USER_INFO_SUCCESS] 获取用户信息成功 - userId={}, username={}, email={}", 
                user.getId(), user.getUsername(), user.getEmail());
        
        return response;
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        log.info("[AUTH][CHANGE_PASSWORD] 收到修改密码请求 - userId={}", userId);
            
        // 1. 获取用户信息
        User user = userRepository.selectById(userId);
        if (user == null) {
            log.error("[AUTH][CHANGE_PASSWORD] 用户不存在 - userId={}", userId);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
            
        // 2. 验证旧密码与数据库密码是否一致
        if (!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())) {
            log.warn("[AUTH][CHANGE_PASSWORD] 旧密码验证失败 - userId={}", userId);
            throw new BusinessException(ResultCode.OLD_PASSWORD_WRONG);
        }
            
        // 3. 更新用户密码
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.updateById(user);
            
        log.info("[AUTH][CHANGE_PASSWORD_SUCCESS] 密码修改成功 - userId={}, username={}", 
                userId, user.getUsername());
    
        // 4. 将该用户加入黑名单（使该用户所有 token 失效）
        String userBlacklistKey = "user:logout:" + userId;
        redisTemplate.opsForValue().set(userBlacklistKey, "1", 7, TimeUnit.DAYS);
            
        log.info("[AUTH][CHANGE_PASSWORD_COMPLETE] 密码修改流程完成 - 用户所有 Token 已失效，blacklistExpire=7days", userId);
    }
}
