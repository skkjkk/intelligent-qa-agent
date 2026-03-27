package com.jujiu.agent.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 * 【设计目的】
 * 集中管理所有工具，提供统一的工具查找和执行入口
 * 【为什么叫"注册中心"？】
 * 类似于"餐厅菜单"或"电话簿"：
 * - 工具们"注册"自己的信息
 * - 需要时从这里"查找"
 * 【为什么用 Map 存储？】
 * - Key：工具名称（如 "weather"）
 * - Value：工具实例
 * - O(1) 时间复杂度查找，比 List 的 O(n) 更快
 * 【为什么用 ConcurrentHashMap？】
 * - 工具注册操作可能在多线程环境下进行
 * - 保证线程安全，防止并发问题
 * 【@PostConstruct 的作用】
 * Spring 创建 Bean 后自动调用此方法
 * 用于初始化时打印已注册的工具列表
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/24 15:54
 */
@Component
@Slf4j
public class ToolRegistry {

    /**
     * 工具注册表
     * Key：工具名称
     * Value：工具实例
     */
    private final Map<String, AbstractTool> toolRegistry = new ConcurrentHashMap<>();


    /**
     * 注册工具
     * 【设计目的】
     * 将工具注册到注册表中，供后续查找使用
     * 【为什么要 public？】
     * 子类工具的 @PostConstruct 或构造函数会调用此方法
     * 【为什么不在构造函数直接put？】
     * 因为子类构造时，父类可能还没完全初始化
     * 用单独的 register() 方法更安全
     */
    public void register(AbstractTool tool) {
        if (tool == null || tool.getName() == null) {
            log.warn("工具注册失败，工具为空或名称为空: tool={}", tool);
            return;
        }

        String toolName = tool.getName();
        toolRegistry.put(toolName, tool);
        log.info("[工具注册] 已注册工具：name={}, class={}", toolName, tool.getClass().getSimpleName());
    }

    /**
     * 获取指定名称的工具
     *
     * 【设计目的】
     * 调用者根据工具名称查找对应的工具实例
     *
     * 【找不到怎么办？】
     * 返回 null，由调用者决定如何处理
     */
    public AbstractTool getTool(String name) {
        return toolRegistry.get(name);
    }

    /**
     * 获取所有已注册的工具
     *
     * 【设计目的】
     * 用于 /tools 接口，返回给前端展示所有可用工具
     */
    public List<AbstractTool> getAllTools() {
        return List.copyOf(toolRegistry.values());
    }

    /**
     * 检查是否存在指定名称的工具
     *
     * 【设计目的】
     * 调用者在执行工具之前，先检查工具是否存在
     */
    public boolean hasTool(String name) {
        return toolRegistry.containsKey(name);
    }
    
    /**
     * 获取已注册的工具数量
     *
     * 【设计目的】
     * 用于快速了解当前已注册的工具数量
     */
    public int getToolCount() {
        return toolRegistry.size();
    }

    /**
     * 初始化完成后的回调
     *
     * 【@PostConstruct 注解的作用】
     * 在 Bean 初始化完成后（构造函数执行 + 依赖注入完成后）自动调用
     * 此时所有工具都已经注册完毕
     *
     * 【设计目的】
     * 打印注册汇总日志，方便调试和确认
     */
    @PostConstruct
    public void onInit(){
        log.info("================= 工具注册完成 =================");
        log.info("[工具初始化] 已注册工具数量：{}", toolRegistry.size());
        for (String name : toolRegistry.keySet()) {
            AbstractTool tool = toolRegistry.get(name);
            log.info("  - {}: {}", name, tool.getDescription());
        }
        log.info("==============================================");
    }

    /**
     * 卸载工具
     *
     * @param name 工具名称
     */
    public void unregister(String name) {
        AbstractTool removed = toolRegistry.remove(name);
        if (removed != null) {
            log.info("[工具注册] 已卸载工具：name={}", name);
        }
    }

}
