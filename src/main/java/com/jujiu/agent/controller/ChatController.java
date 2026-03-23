package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.CreateSessionRequest;
import com.jujiu.agent.model.dto.request.SendMessageRequest;
import com.jujiu.agent.model.dto.response.ChatResponse;
import com.jujiu.agent.model.dto.response.SessionDetailResponse;
import com.jujiu.agent.model.dto.response.SessionResponse;
import com.jujiu.agent.security.JwtTokenProvider;
import com.jujiu.agent.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/22 15:49
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "对话管理", description = "会话创建、消息发送、历史管理")
@Slf4j
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 从 Spring Security 上下文中获取当前登录用户的 ID
     *
     * @return 当前用户 ID，如果未认证则可能抛出异常
     */
    private Long getCurrentUserId() {
        // 从 SecurityContext 中获取认证信息
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        // 返回存储在 authentication details 中的用户 ID
        return (Long) authentication.getDetails();
    }

    @Operation(summary = "创建会话", description = "创建新的对话会话")
    @PostMapping("/session")
    public Result<SessionResponse> createSession(@RequestBody @Valid CreateSessionRequest request) {
        Long userId = getCurrentUserId();
        SessionResponse response = chatService.createSession(userId, request);
        return Result.success(response, "会话创建成功");
    }

    @Operation(summary = "发送消息", description = "向指定会话发送消息，AI会自动回复")
    @PostMapping("/send")
    public Result<ChatResponse> sendMessage(@RequestBody @Valid SendMessageRequest request) {
        Long userId = getCurrentUserId();
        ChatResponse response = chatService.sendMessage(userId, request);
        return Result.success(response);
    }

    @Operation(summary = "获取会话列表", description = "获取当前用户的所有会话列表")
    @GetMapping("/sessions")
    public Result<List<SessionResponse>> getSessionList(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        Long userId = getCurrentUserId();
        List<SessionResponse> sessionList = chatService.getSessionList(userId, page, size);
        return Result.success(sessionList);
    }

    @Operation(summary = "获取会话详情", description = "获取指定会话的所有消息记录")
    @GetMapping("/session/{sessionId}")
    public Result<SessionDetailResponse> getSessionDetail(@PathVariable String sessionId){
        Long userId = getCurrentUserId();
        SessionDetailResponse sessionDetail = chatService.getSessionDetail(userId, sessionId);
        return Result.success(sessionDetail);

    }

    @Operation(summary = "删除会话", description = "删除指定会话及其所有消息")
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId){
        Long userId = getCurrentUserId();
        chatService.deleteSession(userId, sessionId);
        return Result.success(null, "会话删除成功");
    }
}
