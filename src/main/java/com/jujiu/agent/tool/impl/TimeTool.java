package com.jujiu.agent.tool.impl;

import com.jujiu.agent.model.dto.deepseek.ToolDefinition;
import com.jujiu.agent.tool.AbstractTool;

import java.util.Map;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/27 14:49
 */
public class TimeTool extends AbstractTool {
    @Override
    public String getName() {
        return "time";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String execute(Map<String, Object> params) {
        // 1. 从params中获取参数
        String time = (String) params.get("time") != null ? (String) params.get("time") : null;
        
        // 2. 验证参数
        if (time == null) {
            return "参数错误";
        }
        
        // 3. 执行任务
        String result = getTimeFormat(time);
        return "当前时间是：" + result;

    }

    private String getTimeFormat(String time) {
        return null;
    }

    @Override
    public ToolDefinition.Parameters getParameters() {
        return null;
    }
}
