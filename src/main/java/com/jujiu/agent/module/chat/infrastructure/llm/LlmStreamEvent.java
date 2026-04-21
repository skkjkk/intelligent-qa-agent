package com.jujiu.agent.module.chat.infrastructure.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 统一的流式事件模型。
 * <p>
 * 该对象用于屏蔽不同提供商的流式协议差异，业务层只消费这里定义的事件类型。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/21 16:39
 */
@Data
public class LlmStreamEvent {

    /** 流式事件类型。 */
    public enum Type {
        CONTENT,
        TOOL_CALL_DELTA,
        FINISH
    }

    /** 当前事件类型。 */
    @Schema(description = "事件类型", title = "事件类型")
    private Type type;

    /** 内容增量。 */
    @Schema(description = "事件内容", title = "事件内容")
    private String content;

    /** 工具调用增量。 */
    @Schema(description = "工具调用 delta", title = "工具调用 delta")
    private List<ToolCallDelta> toolCallDeltas;

    /** 当前轮次的完成原因。 */
    @Schema(description = "完成原因", title = "完成原因")
    private String finishReason;

    /** 提示词 Token 数。 */
    @Schema(description = "提示词Tokens数", title = "Prompt Token 数")
    private Integer promptTokens;

    /** 生成内容 Token 数。 */
    @Schema(description = "生成内容Tokens数", title = "Completion Token 数")
    private Integer completionTokens;

    /** 总 Token 数。 */
    @Schema(description = "总Tokens数", title = "Total Token 数")
    private Integer totalTokens;

    /**
     * 创建内容事件。
     *
     * @param content 文本增量
     * @return 统一流式事件
     */
    public static LlmStreamEvent content(String content) {
        LlmStreamEvent event = new LlmStreamEvent();
        event.setType(Type.CONTENT);
        event.setContent(content);
        return event;
    }

    /**
     * 创建工具调用增量事件。
     *
     * @param toolCallDeltas 工具调用增量
     * @return 统一流式事件
     */
    public static LlmStreamEvent toolCallDelta(List<ToolCallDelta> toolCallDeltas) {
        LlmStreamEvent event = new LlmStreamEvent();
        event.setType(Type.TOOL_CALL_DELTA);
        event.setToolCallDeltas(toolCallDeltas);
        return event;
    }

    /**
     * 创建结束事件。
     *
     * @param finishReason 完成原因
     * @param promptTokens 提示词 Token 数
     * @param completionTokens 生成内容 Token 数
     * @param totalTokens 总 Token 数
     * @return 统一流式事件
     */
    public static LlmStreamEvent finish(String finishReason,
                                        Integer promptTokens,
                                        Integer completionTokens,
                                        Integer totalTokens) {
        LlmStreamEvent event = new LlmStreamEvent();
        event.setType(Type.FINISH);
        event.setFinishReason(finishReason);
        event.setPromptTokens(promptTokens);
        event.setCompletionTokens(completionTokens);
        event.setTotalTokens(totalTokens);
        return event;
    }

    /**
     * 统一的工具调用增量结构。
     */
    @Data
    public static class ToolCallDelta {
        /** 工具调用在当前响应中的顺序索引。 */
        private Integer index;

        /** 工具调用唯一标识。 */
        private String id;

        /** 工具调用类型，当前固定为 function。 */
        private String type;

        /** 函数增量信息。 */
        private FunctionDelta function;
    }

    /**
     * 工具函数增量结构。
     */
    @Data
    public static class FunctionDelta {
        /** 工具名称。 */
        private String name;

        /** 工具参数增量 JSON。 */
        private String arguments;
    }
}
