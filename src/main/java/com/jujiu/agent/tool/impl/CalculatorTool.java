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
        // 1. 从params获取expression参数
        String expression = (String) params.get("expression");
        
        // 2. 校验参数
        if (expression == null) {
            return "错误：缺少必填参数 expression（数学表达式）";
        }

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


//    private double calculate(String expression) {
//        // 去除空格
//        expression = expression.replaceAll("\\s+", "");
//
//        // 数字栈
//        ArrayDeque<Double> numStack  = new ArrayDeque<>();
//        
//        // 运算符栈
//        ArrayDeque<Character> opStack  = new ArrayDeque<>();
//        
//        int i = 0;
//
//        while (i < expression.length()) {
//            // 每次循环开始从 i 读字符
//            char c = expression.charAt(i);
//
//            if (Character.isDigit(c) || c == '.') {
//                // 情况1：读取完整的数字（可能多位），如“123”
//                // 使用一个StringBuilder来存储数字，知道不是数字为止
//                StringBuilder sb = new StringBuilder();
//                while (i < expression.length() && (Character.isDigit(c) || c == '.')) {
//                    sb.append(c);
//                    ++i;
//                    if (i < expression.length()) {
//                        c = expression.charAt(i);
//                    }
//                }
//                numStack.push(Double.parseDouble(sb.toString()));
//                continue;
//            } else if (c == '(') {
//                // 情况2：左括号，直接压栈
//                opStack.push(c);
//            } else if (c == ')') {
//                // 情况3：右括号，计算到左括号为止
//                // while 运算符栈顶不是'('，就调用compute()
//                // 最后弹出'('
//                while (!opStack.isEmpty() && opStack.peek() != '(') {
//                    compute(numStack, opStack);
//                }
//                opStack.pop();
//            } else if (c == '+' || c == '-' || c == '*' || c == '/') {
//                // 情况4：运算符，根据优先级进行计算
//                // while栈顶不为空&& 栈顶不是'(' && 栈顶优先级>=当前运算符优先级
//                // 调用compute()
//                // 将当前运算符压栈
//                while (!opStack.isEmpty() && opStack.peek() != '(' && priority(opStack.peek()) >= priority(c)) {
//                    compute(numStack, opStack);
//                }
//                opStack.push(c);
//            }
//            ++i;
//        }
//        
//        // 循环结束后，栈中应该只有一个数字，即结果
//        while (!opStack .isEmpty()) {
//            compute(numStack, opStack );
//        }
//        return numStack.pop();
//    }
    
    private int priority(char operator) {
        if (operator == '+' || operator == '-') {
            return 1;
        }
        if (operator == '*' || operator == '/') {
            return 2;
        }
        // (的优先级最低
        return 0;
    }

    private void compute(Deque<Double> numStack, Deque<Character> opStack) {
        Character op = opStack.pop();
        // 弹出右操作数
        Double b = numStack.pop();
        // 弹出左操作数
        Double a = numStack.pop();

        Double result = switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> a / b;
            default -> throw new IllegalArgumentException("非法运算符: " + op);
        };

        // 将结果压回数字栈
        numStack.push(result);
    }
    
}
