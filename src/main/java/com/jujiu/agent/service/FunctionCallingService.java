package com.jujiu.agent.service;

import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;

import java.util.List;

/**
 * Function Calling 服务接口
 * 负责管理工具调用的完整流程
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/25
 */
public interface FunctionCallingService {
    /**
     * 带工具调用的对话
     * AI会自动判断是否需要调用工具，并执行多轮调用直到获得最终答案
     *
     * @param messages 对话消息列表
     * @return AI的最终回复结果
     */
    DeepSeekResult chatWithTools(List<DeepSeekMessage> messages);
    
}
