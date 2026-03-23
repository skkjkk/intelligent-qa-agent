package com.jujiu.agent.service;

import com.jujiu.agent.model.dto.request.*;
import com.jujiu.agent.model.dto.response.LoginResponse;
import com.jujiu.agent.model.dto.response.UserInfoResponse;

/**
 * 认证服务
 * @author 17644
 */
public interface AuthService {
    /**
     * 登录
     *
     * @param loginRequest 登录请求
     * @return token
     */
    LoginResponse login(LoginRequest loginRequest);

    /**
     * 注册
     *
     * @param registerRequest 注册请求
     * @return token
     */
    LoginResponse register(RegisterRequest registerRequest);

    /**
     * 刷新令牌
     *
     * @param refreshRequest 刷新令牌请求
     * @return token
     */
    LoginResponse refresh(RefreshRequest refreshRequest);

    /**
     * 退出登录
     * @param logoutRequest 退出登录请求
     */
    void logout(LogoutRequest logoutRequest);


    /**
     * 获取当前用户
     * @param userId 用户ID
     * @return 当前用户
     */
    UserInfoResponse getCurrentUser(Long userId);
    
    
    /**
     * 修改密码
     *
      * @param userId 用户ID
     * @param changePasswordRequest 修改密码请求
     */
    void changePassword(Long userId, ChangePasswordRequest changePasswordRequest);
}
