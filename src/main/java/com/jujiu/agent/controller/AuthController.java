package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.*;
import com.jujiu.agent.model.dto.response.LoginResponse;
import com.jujiu.agent.model.dto.response.UserInfoResponse;
import com.jujiu.agent.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证管理控制器
 * 
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/21 15:43
 */
@Slf4j
@Tag(name = "认证管理", description = "认证管理相关接口")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;

    private Long getCurrentUserId() {
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getDetails();
    }
    
    @Operation(summary = "用户登录", description = "用户登录接口")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return Result.success(response, "登录成功");
    }
    
    @Operation(summary = "用户注册", description = "用户注册接口")
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest registerRequest) {
        LoginResponse response = authService.register(registerRequest);
        return Result.success(response, "注册成功");
    }

    @Operation(summary = "Token 刷新", description = "使用刷新令牌获取新的访问令牌")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody @Valid RefreshRequest refreshRequest) {
        LoginResponse response = authService.refresh(refreshRequest);
        return Result.success(response, "刷新成功");
    }

    @Operation(summary = "用户退出", description = "用户退出接口")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody @Valid LogoutRequest logoutRequest) {
       authService.logout(logoutRequest);
        return Result.success(null,"退出成功");
    }
    
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的信息")
    @GetMapping("/me")
    public Result<UserInfoResponse> getCurrentUser() {
        Long userId = getCurrentUserId();
        UserInfoResponse response = authService.getCurrentUser(userId);
        return Result.success(response);
    }

    @Operation(summary = "修改用户密码", description = "修改当前登录用户密码")
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest changePasswordRequest) {
        Long userId = getCurrentUserId();
        authService.changePassword(userId, changePasswordRequest);
        return Result.success(null, "修改密码成功");
    }
}
