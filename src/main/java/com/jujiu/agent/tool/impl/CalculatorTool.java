package com.jujiu.agent.tool.impl;

import com.jujiu.agent.model.dto.deepseek.ToolDefinition;
import com.jujiu.agent.tool.AbstractTool;
import com.jujiu.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/26 11:11
 */
@Component
@Slf4j
public class CalculatorTool extends AbstractTool {

    // Constructor to register the tool with the registry
    public CalculatorTool(ToolRegistry toolRegistry) {
        // 向工具注册表中注册当前工具
        toolRegistry.register(this);
    }

    @Override
    public String getName() {
        // 工具唯一标识符
        return "calculator";
    }

    @Override
    public String getDescription() {
        // 写清楚：能做什么、需要什么参数
        return "专业数学计算工具，支持复杂数学表达式求值。" +
                "支持：加减乘除、幂运算(^)、开方(sqrt)、三角函数(sin/cos/tan)、" +
                "对数(log/log10/log2)、常量(pi/e)等。" +
                "参数：expression（必填，数学表达式，如'sin(pi/2)'、'sqrt(16)+2^3'）。" +
                "返回：计算结果。";
    }

    @Override
    public String execute(Map<String, Object> params) {
        // 1. 从params获取expression参数校验参数
        Object exprObj = params.get("expression");
        if (!(exprObj instanceof String)) {
            return "错误：expression 参数必须是字符串";
        }
        
        String expression = (String) exprObj;
        
        try {
            // 3. 调用计算方法
            double result = calculate(expression);
            
            // 检查结果是否非法
            if (Double.isInfinite(result) || Double.isNaN(result)) {
                return "计算结果非法，请检查表达式";
            }
            
            // 4. 返回结果
            String resultStr;
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                // 5.0 → "5"
                resultStr = String.valueOf((int) result);
            } else {
                // 2.5 → "2.5"
                resultStr = String.valueOf(result);
            }
            return "计算结果：" + expression + " = " + resultStr;
            
        } catch (Exception e) {
            log.error("计算错误", e);
            return "计算失败：表达式不合法，请检查格式（示例：(2+3)*4）";
        }
        
    }
    
    private double calculate(String expression) {
        return new ExpressionBuilder(expression)
                .build()
                .evaluate();
    }
    
    @Override
    public ToolDefinition.Parameters getParameters() {
        // 1. 创建参数定义对象
        ToolDefinition.Parameters parameters = new ToolDefinition.Parameters();
        parameters.setType("object");
        
        // 2. 定义expression参数
        ToolDefinition.Property property = new ToolDefinition.Property();
        property.setType("string");
        property.setDescription("数学表达式，如'(2+3)*4'");
        
        // 3. 将参数添加到parameters
        Map<String, ToolDefinition.Property> properties = new HashMap<>();
        properties.put("expression", property);
        parameters.setProperties(properties);
        
        // 4. 设置required
        List<String> require = new ArrayList<>();
        require.add("expression");
        parameters.setRequired(require);
        
        // 5. 返回参数定义对象
        return parameters;
    }
}
