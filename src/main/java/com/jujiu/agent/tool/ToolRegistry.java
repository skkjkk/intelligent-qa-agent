package com.jujiu.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jujiu.agent.model.entity.Tool;
import com.jujiu.agent.repository.ToolRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private ObjectMapper objectMapper;
    
    // 缓存：工具名称 -> 实现实例
    private final Map<String, AbstractTool> toolImplementations  = new ConcurrentHashMap<>();

    // 缓存：工具名称 -> 数据库配置
    private final Map<String, Tool> toolConfigs = new ConcurrentHashMap<>();
    
    // 启动时加载
    @PostConstruct
    public void init() {
        log.info("[TOOL_REGISTRY] 开始初始化...");
        // 1. 扫描所有代码实现的工具
        scanCodeImplementations();
        log.info("[TOOL_REGISTRY] 代码实现扫描完成，数量: {}", toolImplementations.size());
        // 2. 加载数据库配置，并校验一致性
        loadDatabaseConfig();
        log.info("[TOOL_REGISTRY] 数据库配置加载完成");
    }

    /**
     * 加载数据库配置，校验与代码实现的一致性
     */
    private void loadDatabaseConfig() {
        List<Tool> dbTools = toolRepository.selectList(null);
        
        for (Tool dbTool : dbTools) {
            String className = dbTool.getClassName();
            AbstractTool implementation  = toolImplementations.get(className);
            
            if (implementation == null) {
                // 数据库配了，但代码没实现 -> 严重错误，启动失败
                log.error("[TOOL_REGISTRY] 数据库配置了未实现的工具: className={}, toolName={}",
                        className, dbTool.getToolName());
                throw new IllegalStateException("工具配置错误: " + dbTool.getToolName() +
                        " 的实现类 " + className + " 不存在");
            }

            // 校验名称一致性
            if (!implementation.getName().equals(dbTool.getToolName())) {
                log.warn("[TOOL_REGISTRY] 工具名称不一致: 数据库={}, 代码实现={}, className={}",
                        dbTool.getToolName(), implementation.getName(), className);
            }
            
            toolConfigs.put(dbTool.getToolName(), dbTool);
            log.info("[TOOL_REGISTRY] 加载工具配置: {} [status={}]",
                    dbTool.getToolName(), dbTool.getStatus());
        }
        
    }

    /**
     * 扫描代码中所有 AbstractTool 实现
     */
    private void scanCodeImplementations() {
        Map<String, AbstractTool> beans = applicationContext.getBeansOfType(AbstractTool.class);
        for (AbstractTool tool : beans.values()) {
            String className = tool.getClass().getName();
            // 用className作为Key，工具实例作为Value
            toolImplementations.put(className, tool);
            log.info("[TOOL_REGISTRY] 扫描到工具实现: {} -> {}", className, tool.getName());
        }
    }
    
    /**
     * 获取启用的工具列表（供 FunctionCallingService 使用）
     */
    public List<Tool> getEnabledTools() {
        List<Tool> enabled = new ArrayList<>();
        for (Tool tool : toolConfigs.values()) {
            if (tool.getStatus() != null && tool.getStatus() == 1) {
                enabled.add(tool);
            }
        }
        return enabled;
    }

    /**
     * 根据工具名称获取实现
     */
    public AbstractTool getImplementation(String toolName) {
        Tool config = toolConfigs.get(toolName);
        if (config == null || config.getStatus() == 0) {
            // 不存在或被禁用
            return null; 
        }
        return toolImplementations.get(config.getClassName());
    }

    /**
     * 刷新配置（供管理接口调用）
     */
    public void refresh() {
        toolConfigs.clear();
        loadDatabaseConfig();
    }
}
