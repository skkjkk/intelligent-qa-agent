package com.jujiu.agent.service;

import com.jujiu.agent.model.dto.request.CreateSessionRequest;
import com.jujiu.agent.model.dto.request.SendMessageRequest;
import com.jujiu.agent.model.dto.response.ChatResponse;
import com.jujiu.agent.model.dto.response.SessionDetailResponse;
import com.jujiu.agent.model.dto.response.SessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * @author 17644
 */
public interface ChatService {

    /**
     * 创建会话
     * @param userId  用户 ID
     * @param request 创建会话请求
     * @return 会话信息
     */
    SessionResponse createSession(Long userId, CreateSessionRequest request);

    /**
     * 发送消息（调用AI并保存对话）
     * @param userId 当前用户ID
     * @param request 发送消息请求
     * @return AI回复响应
     */
    ChatResponse sendMessage(Long userId, SendMessageRequest request);

    /**
     * 获取用户的会话列表
     * @param userId 当前用户ID
     * @param page 页码
     * @param size 每页数量
     * @return 会话列表
     */
    List<SessionResponse> getSessionList(Long userId, Integer page, Integer size);
    
    /**
     * 获取会话详情（包含所有消息）
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return 会话详情
     */
    SessionDetailResponse getSessionDetail(Long userId, String sessionId);
    
    /**
     * 删除会话
     * @param userId 当前用户ID（用于权限校验）
     * @param sessionId 会话ID
     */
    void deleteSession(Long userId, String sessionId);

    /**
     * 流式发送消息（AI回复以SSE流式返回）
     * @param userId 当前用户ID
     * @param request 发送消息请求
     * @return SseEmitter 用于向客户端推送流式数据
     */
    SseEmitter sendMessageStream(Long userId, SendMessageRequest request);
}
