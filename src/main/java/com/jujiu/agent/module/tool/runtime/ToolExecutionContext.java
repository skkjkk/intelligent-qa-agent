package com.jujiu.agent.module.tool.runtime;

/**
 * 工具执行上下文。
 *
 * <p>用于在一次工具执行链路中透传当前用户等上下文信息，
 * 避免在现有 {@code AbstractTool.execute(Map<String, Object>)} 签名不变的前提下，
 * 丢失与权限、知识库访问等相关的执行上下文。
 *
 * <p>当前版本仅透传用户 ID，后续如有需要可继续扩展会话 ID、请求来源等字段。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/11
 */
public final class ToolExecutionContext {
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private ToolExecutionContext() {
    }

    /**
     * 设置当前工具执行用户 ID。
     *
     * @param userId 当前用户 ID
     */
    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * 获取当前工具执行用户 ID。
     *
     * @return 当前用户 ID，不存在时返回 {@code null}
     */
    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 清理当前工具执行上下文。
     */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
