package com.jujiu.agent.tool;

import com.jujiu.agent.model.dto.deepseek.ToolDefinition;

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
     * 获取工具名称
     *
     * 【设计目的】
     * 工具的唯一标识，用于：
     * - 调用时根据名称找到对应工具
     * - AI决定使用哪个工具的依据
     *
     * 【约束】
     * 子类必须返回唯一的名称，如 "weather"、"calculator"
     */
    public abstract String getName();

    /**
     * 获取工具描述
     *
     * 【设计目的】
     * 描述工具的能力，用于：
     * - /tools 接口返回给前端展示
     * - AI根据描述决定何时调用此工具
     *
     * 【最佳实践】
     * 描述应该清晰说明：
     * - 工具能做什么
     * - 需要什么参数
     * - 返回什么结果
     */
    public abstract String getDescription();
    /**
     * 执行工具
     *
     * 【设计目的】
     * 这是工具的核心方法，系统统一调用此方法执行工具
     *
     * 【为什么参数用 Map？】
     * - 不同工具的参数不同（天气需要city，计算器需要expression）
     * - 用 Map 可以灵活接收任意结构的参数
     * - 不需要为每个工具定义不同的参数类
     *
     * 【为什么返回 String？】
     * - 统一返回格式，便于处理
     * - 无论工具内部多复杂，对外接口统一
     *
     * @param params 工具参数（如 {"city": "北京"}）
     * @return 执行结果
     */
    public abstract String execute(Map<String, Object> params);

    /**
     * 获取工具参数定义
     *
     * 【设计目的】
     * 定义工具需要哪些参数，用于：
     * - Function Calling 时告诉 AI 需要传什么参数
     * - 参数校验和文档生成
     *
     * 【返回格式】
     * 返回符合 JSON Schema 的参数定义对象
     * 包含：参数类型、属性列表、必填字段
     *
     * @return 参数定义对象
     */
    public abstract ToolDefinition.Parameters getParameters();
}
