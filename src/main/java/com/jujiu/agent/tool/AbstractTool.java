package com.jujiu.agent.tool;

import com.jujiu.agent.model.dto.response.ToolExecutionResult;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * 工具抽象基类
 
 * 【设计目的】
 * 所有工具（天气、计算器、搜索等）都必须继承此类
 * 统一工具的调用方式，让系统可以"统一管理"
 
 * 【为什么用抽象类而不是接口？】
 * 1. 抽象类可以提供公共属性（如 name、description）
 * 2. 子类必须实现抽象方法，保证规范统一
 * 3. 接口只能声明方法，公共属性需要另外处理
 
 * 【设计模式】
 * 这是一种"模板方法模式"的变体
 * 子类只需关注 execute() 的具体实现，其他方法提供元数据
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 15:50
 */
public abstract class AbstractTool {
    /**
     * 工具名称（用于日志和关联，不再用于配置）
     * 注意：必须与数据库 tool.tool_name 一致
     */
    public abstract String getName();
    
    /**
     * 执行工具（参数已经从数据库配置验证过）
     */
    public abstract String execute(Map<String, Object> params);

    /**
     * 默认参数（作为数据库未配置时的 fallback）
     */
    public Map<String, Object> getParameters() {
        // 默认返回空，子类可覆盖
        return new HashMap<>();
    }

    public ToolExecutionResult executeStructured(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            String result = execute(params);
            return ToolExecutionResult.builder()
                    .success(true)
                    .message("工具执行成功")
                    .data(result)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return ToolExecutionResult.builder()
                    .success(false)
                    .message("工具执行失败")
                    .errorCode("TOOL_EXECUTE_ERROR")
                    .data(null)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
