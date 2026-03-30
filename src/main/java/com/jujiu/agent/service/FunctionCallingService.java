package com.jujiu.agent.service;

import com.jujiu.agent.client.DeepSeekResult;
import com.jujiu.agent.model.dto.deepseek.DeepSeekMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Consumer;

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

    /**
     * 带工具调用的流式对话
     * 支持流式输出、Function Calling、工具多轮调用，最终返回完整AI回复和Token汇总
     *
     * @param messages      对话消息列表
     * @param eventConsumer 流式事件消费者
     * @return 最终对话结果及Token统计
     */
    StreamingChatResult streamChatWithTools(
            List<DeepSeekMessage> messages,
            Consumer<StreamEvent> eventConsumer
    );

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class StreamingChatResult {
        // 最终回复
        private String finalReply;

        // 提示词的
        private int promptTokens;

        // AI回复的Token数量
        private int completionTokens;
        
        // 总共使用的Token数量
        private int totalTokens;

        // 需要保存到数据库的消息列表（包括带 tool_calls 的 assistant 消息）
        private List<DeepSeekMessage> messagesToSave;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class StreamEvent {
        // 事件类型
        private String type;

        // 事件内容
        private String content;

        // 工具名称
        private String toolName;

        // 工具调用是否成功
        private Boolean success;
        
        // 错误信息
        private String errorMessage;

        public static StreamEvent message(String content) {
            return new StreamEvent("message", content, null, null, null);
        }

        public static StreamEvent toolStart(String toolName) {
            return new StreamEvent("tool_start", null, toolName, null, null);
        }

        public static StreamEvent toolEnd(String toolName, boolean success, String errorMessage) {
            return new StreamEvent("tool_end", null, toolName, success, errorMessage);
        }
    }
}
