package com.jujiu.agent.module.tool.application.registry;

import com.jujiu.agent.module.tool.domain.entity.Tool;
import com.jujiu.agent.module.tool.infrastructure.mapper.ToolMapper;
import com.jujiu.agent.module.tool.runtime.AbstractTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
public class ToolRegistry implements SmartInitializingSingleton {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ToolMapper toolMapper;
    
    // 缓存：工具名称 -> 实现实例
    private final Map<String, AbstractTool> toolImplementations  = new ConcurrentHashMap<>();

    // 缓存：工具名称 -> 数据库配置
    private final Map<String, Tool> toolConfigs = new ConcurrentHashMap<>();

    /**
     * 在所有单例 Bean 初始化完成后执行工具注册。
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info("[TOOL_REGISTRY] 开始初始化...");

        scanCodeImplementations();
        log.info("[TOOL_REGISTRY] 代码实现扫描完成，数量: {}", toolImplementations.size());

        loadDatabaseConfig();
        log.info("[TOOL_REGISTRY] 数据库配置加载完成");
    }

    /**
     * 加载数据库配置，校验与代码实现的一致性
     * 从数据库读取工具配置信息，检查每个工具是否有对应的代码实现，
     * 并验证数据库配置的工具名称与代码实现是否一致。最终将有效的工具配置注册到映射表中。
     */
    private void loadDatabaseConfig() {
        // 从数据库查询所有工具配置
        List<Tool> dbTools = toolMapper.selectList(null);
            
        // 遍历所有数据库工具配置
        for (Tool dbTool : dbTools) {
            String className = dbTool.getClassName();
            AbstractTool implementation  = toolImplementations.get(className);

            log.info("[TOOL_REGISTRY] 校验数据库工具 - dbClassName={}, toolName={}, matched={}",
                    className, dbTool.getToolName(), implementation != null);

            // 检查代码中是否存在对应的实现类
            if (implementation == null) {
                log.error("[TOOL_REGISTRY] 数据库配置了未实现的工具，已跳过加载 - className={}, toolName={}",
                        className, dbTool.getToolName());
                continue;
            }
            
            // 校验名称一致性
            if (!implementation.getName().equals(dbTool.getToolName())) {
                log.warn("[TOOL_REGISTRY] 工具名称不一致：数据库={}, 代码实现={}, className={}",
                        dbTool.getToolName(), implementation.getName(), className);
            }
                
            // 将有效工具配置注册到映射表
            toolConfigs.put(dbTool.getToolName(), dbTool);
            log.info("[TOOL_REGISTRY] 加载工具配置：{} [status={}]",
                    dbTool.getToolName(), dbTool.getStatus());
        }
            
    }

    /**
     * 扫描代码中所有 AbstractTool 实现
     * 从 Spring 应用上下文中获取所有 AbstractTool 类型的 Bean，
     * 并将其注册到工具实现映射表中，以便后续根据类名查找和调用。
     */
    private void scanCodeImplementations() {
        // 从 Spring 容器获取所有 AbstractTool 类型的 Bean
        Map<String, AbstractTool> beans = applicationContext.getBeansOfType(AbstractTool.class);
        
        // 遍历所有工具实例并注册到映射表
        for (AbstractTool tool : beans.values()) {
            String className = tool.getClass().getName();
            toolImplementations.put(className, tool);
            
            log.info("[TOOL_REGISTRY] 扫描到工具实现 - className={}, toolName={}",
                    className, tool.getName());
        }
    }
    
    /**
     * 获取启用的工具列表
     * 遍历所有已加载的工具配置，筛选出状态为启用（status=1）的工具。
     * 该列表供 FunctionCallingService 使用，用于确定 AI 可以调用的工具范围。
     *
     * @return List<Tool> 启用的工具配置列表
     */
    public List<Tool> getEnabledTools() {
        // 存储启用的工具列表
        List<Tool> enabled = new ArrayList<>();
        
        // 遍历所有工具配置，筛选出启用的工具
        for (Tool tool : toolConfigs.values()) {
            if (tool.getStatus() != null && tool.getStatus() == 1) {
                enabled.add(tool);
            }
        }
        
        return enabled;
    }

    /**
     * 根据工具名称获取实现
     * 从已加载的工具配置中查找指定工具，如果工具存在且处于启用状态，
     * 则返回对应的代码实现；否则返回 null。
     *
     * @param toolName 工具名称，用于查找对应的实现
     * @return AbstractTool 工具实现对象，如果工具不存在或被禁用则返回 null
     */
    public AbstractTool getImplementation(String toolName) {
        // 从配置映射表获取工具配置
        Tool config = toolConfigs.get(toolName);
        if (config == null || config.getStatus() == 0) {
            // 不存在或被禁用
            return null; 
        }
        // 根据类名从实现映射表获取工具实例
        return toolImplementations.get(config.getClassName());
    }

    /**
     * 刷新数据库配置。
     */
    public void refresh() {
        toolConfigs.clear();
        loadDatabaseConfig();
    }
}
