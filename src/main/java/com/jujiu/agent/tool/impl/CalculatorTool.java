package com.jujiu.agent.tool.impl;

import com.jujiu.agent.tool.AbstractTool;
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
    public CalculatorTool() {
    }

    @Override
    public String getName() {
        // 工具唯一标识符
        return "calculator";
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
    
}
