package com.jujiu.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.constant.BusinessConstants;
import com.jujiu.agent.common.constant.RedisKeys;
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
 * 认证服务实现类，负责登录、注册、登出、Token刷新、密码修改等认证相关操作。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 15:44
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    /** 用户数据访问层 */
    @Autowired
    private UserRepository userRepository;
    
    /** JWT Token 提供者，用于生成和验证 Token */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    /** 登录日志数据访问层 */
    @Autowired
    private LoginLogRepository loginLogRepository;
    
    /** Redis 字符串模板，用于缓存和限流等操作 */
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /** 使用 BCryptPasswordEncoder 替代直接的 BCrypt 工具类 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 用户登录。
     *
     * @param loginRequest 登录请求参数
     * @return 登录响应，包含访问令牌和刷新令牌
     * @throws BusinessException 登录失败次数过多、用户不存在或密码错误时抛出
     */
    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        log.info("[AUTH][LOGIN] 收到登录请求 - ip={}", getClientIp());

        // 1. 构建登录失败计数 Redis Key
        String failKey = RedisKeys.getLoginFailKey(loginRequest.getUsername());

        // 2. 检查是否被锁定
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        failCountStr = failCountStr == null ? "0" : failCountStr;
        if (Integer.parseInt(failCountStr) >= BusinessConstants.LOGIN_FAIL_MAX_COUNT) {
            log.warn("[AUTH][LOGIN] 登录失败次数过多，账号已被临时锁定 - failCount={}", failCountStr);
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        
        // 3. 使用 LambdaQueryWrapper 查询用户（类型安全）
        User user = userRepository.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginRequest.getUsername())
        );
        
        // 4. 校验用户是否存在
        if (user == null) {
            log.warn("[AUTH][LOGIN] 用户不存在 - ip={}", getClientIp());
            // 用户不存在，也算一次失败
            recordFailAttempt(failKey);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        // 5. 验证密码（使用 Spring Security 的 BCryptPasswordEncoder）
        String rawPassword = loginRequest.getPassword();
        if (rawPassword == null) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("[AUTH][LOGIN] 密码错误 - ip={}", getClientIp());
            // 密码错误，记录失败
            recordFailAttempt(failKey);
            // 密码错误，保存登录日志
            saveLoginLog(user.getId(), 0);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        
        // 6. 密码验证通过后，清除该用户的黑名单（允许新 Token 生效）
        redisTemplate.delete(RedisKeys.getUserLogoutKey(user.getId()));
                
        // 7. 登录成功，保存登录日志并清除失败记录
        saveLoginLog(user.getId(), 1);
        redisTemplate.delete(failKey);
                
        log.info("[AUTH][LOGIN_SUCCESS] 登录成功 - ip={}", getClientIp());

        log.debug("[AUTH][TOKEN_GEN] 开始生成 Access Token 和 Refresh Token - userId={}", user.getId());
        
        // 8. 生成访问令牌
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), 
                user.getUsername(), 
                user.getRole()
        );
        
        // 9. 生成刷新令牌
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), 
                user.getUsername(), 
                user.getRole()
        );
        
        log.info("[AUTH][LOGIN_COMPLETE] 登录流程完成 - userId={}, tokenType=Bearer, expiresIn={}s", 
                user.getId(), jwtTokenProvider.getAccessTokenExpiration());
        
        // 10. 返回登录响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    /**
     * 用户注册。
     *
     * @param registerRequest 注册请求参数
     * @return 注册成功后的登录响应，包含访问令牌和刷新令牌
     * @throws BusinessException 用户名或邮箱已存在时抛出
     */
    @Override
    public LoginResponse register(RegisterRequest registerRequest) {
        log.info("[AUTH][REGISTER] 收到注册请求");
        
        // 1. 检查用户名是否已存在
        User existUser = userRepository.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, registerRequest.getUsername())
        );
        if (existUser != null) {
            log.warn("[AUTH][REGISTER] 用户名已被注册");
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // 2. 检查邮箱是否已注册
        User existEmail = userRepository.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, registerRequest.getEmail())
        );
        if (existEmail != null) {
            log.warn("[AUTH][REGISTER] 邮箱已被注册");
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
                .role(BusinessConstants.DEFAULT_ROLE)
                .status(BusinessConstants.USER_STATUS_NORMAL)
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
        log.info("[AUTH][REGISTER_COMPLETE] 注册流程完成 - userId={}", newUser.getId());
        
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    /**
     * 保存登录日志。
     *
     * @param userId 用户ID，未知用户用 -1 标记
     * @param status 登录状态：0-失败，1-成功
     */
    private void saveLoginLog(Long userId, int status) {
        // 1. 构建登录日志实体
        LoginLog log = LoginLog.builder()
                .userId(userId)
                .ip(getClientIp())
                .userAgent(getUserAgent())
                .status(status)
                .loginTime(LocalDateTime.now())
                .build();
        // 2. 插入数据库
        loginLogRepository.insert(log);
    }
    
    /**
     * 获取客户端 User-Agent。
     *
     * @return 客户端 User-Agent 字符串，获取失败返回 "unknown"
     */
    private String getUserAgent() {
        // 1. 从请求上下文中获取 ServletRequestAttributes
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        // 2. 从请求头中读取 User-Agent
        HttpServletRequest request = attributes.getRequest();
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }
    
    /**
     * 获取客户端 IP 地址。
     *
     * @return 客户端 IP 地址，获取失败返回 "unknown"
     */
    private String getClientIp() {
        // 1. 从请求上下文中获取 ServletRequestAttributes
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            // 2. 获取当前请求并返回远程地址
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    /**
     * 记录一次登录失败尝试。
     *
     * @param failKey Redis 中存储失败次数的 Key
     */
    private void recordFailAttempt(String failKey) {
        // 1. 失败次数 +1，返回增加后的值
        Long count = redisTemplate.opsForValue().increment(failKey);

        // 2. 如果是第一次设置（count=1），设置锁定时间过期
        if (count != null && count == 1) {
            redisTemplate.expire(failKey, BusinessConstants.LOGIN_FAIL_LOCK_TIME, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 刷新访问令牌。
     * 使用刷新令牌获取新的访问令牌和刷新令牌。
     *
     * @param refreshRequest 刷新令牌请求，包含旧的刷新令牌
     * @return 包含新访问令牌和新刷新令牌的响应
     * @throws BusinessException 刷新令牌无效时抛出
     */
    @Override
    public LoginResponse refresh(RefreshRequest refreshRequest) {
        log.info("[AUTH][REFRESH_TOKEN] 收到 Token 刷新请求");
        
        // 1. 从请求中获取刷新令牌
        String refreshToken = refreshRequest.getRefreshToken();
        
        // 2. 验证刷新令牌的有效性
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("[AUTH][REFRESH_TOKEN] 刷新令牌无效");
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        } 
        
        // 3. 从刷新令牌中提取用户信息
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        // 4. 生成新的 Access Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, role);

        log.info("[AUTH][REFRESH_TOKEN_SUCCESS] Token 刷新成功 - userId={}, role={}", userId, role);
        
        // 5. 返回新的访问令牌和刷新令牌
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    /**
     * 用户登出。
     * 将当前 Token 加入黑名单，使其失效。
     *
     * @param logoutRequest 登出请求参数，包含待失效的 Token
     * @throws BusinessException Token 无效或已过期时抛出
     */
    @Override
    public void logout(LogoutRequest logoutRequest) {
        log.info("[AUTH][LOGOUT] 收到退出请求");
        
        // 1. 从请求中获取待失效的 Token
        String logoutRequestToken = logoutRequest.getToken();
        
        // 2. 验证 Token 的有效性
        if (!jwtTokenProvider.validateToken(logoutRequestToken)) {
            log.warn("[AUTH][LOGOUT] 退出令牌无效");
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        // 3. 解析 Token 获取过期时间，计算剩余有效期
        Claims claims = jwtTokenProvider.parseClaims(logoutRequestToken);
        Date expiration = claims.getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();
        log.info("[AUTH][LOGOUT] Token 剩余有效期 - remainingTime={}ms, expiration={}", 
                remainingTime, expiration);
        
        // 4. 校验 Token 是否已过期
        if (remainingTime <= 0) {
            log.warn("[AUTH][LOGOUT] Token 已过期，无法执行退出操作");
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }
        
        // 5. 将令牌加入黑名单，过期时间与令牌剩余有效时间一致
        String blacklistKey = RedisKeys.getTokenBlacklistKey(logoutRequestToken);
        redisTemplate.opsForValue().set(blacklistKey, "1", remainingTime, TimeUnit.MILLISECONDS);
        
        // 6. 记录退出成功日志
        Long userId = jwtTokenProvider.getUserId(logoutRequestToken);
        log.info("[AUTH][LOGOUT_SUCCESS] 退出成功 - userId={}, token 已加入黑名单，blacklistExpire={}ms", 
                userId, remainingTime);
    }

    /**
     * 获取当前用户信息。
     *
     * @param userId 当前用户ID
     * @return 用户信息响应
     * @throws BusinessException 用户不存在时抛出
     */
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

        log.info("[AUTH][GET_USER_INFO_SUCCESS] 获取用户信息成功 - userId={}", user.getId());
        
        return response;
    }

    /**
     * 修改用户密码。
     * 修改成功后将该用户所有 Token 加入黑名单，强制重新登录。
     *
     * @param userId                 当前用户ID
     * @param changePasswordRequest  修改密码请求参数
     * @throws BusinessException 用户不存在或旧密码错误时抛出
     */
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

        log.info("[AUTH][CHANGE_PASSWORD_SUCCESS] 密码修改成功 - userId={}", userId);

        // 4. 将该用户加入黑名单（使该用户所有 token 失效）
        redisTemplate.opsForValue().set(
                RedisKeys.getUserLogoutKey(userId),
                "1",
                BusinessConstants.USER_LOGOUT_EXPIRE,
                TimeUnit.MILLISECONDS
        );

        log.info("[AUTH][CHANGE_PASSWORD_COMPLETE] 密码修改流程完成 - 用户{} 所有 Token 已失效，blacklistExpire={}ms", 
                userId, BusinessConstants.USER_LOGOUT_EXPIRE);
    }
}
