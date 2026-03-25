package com.jujiu.agent.service;

import com.jujiu.agent.model.dto.request.ExecuteToolRequest;
import com.jujiu.agent.model.dto.response.ExecuteToolResponse;
import com.jujiu.agent.model.dto.response.ToolResponse;

import java.util.List;

/**
 * 工具服务接口
 *
 * 【设计目的】
 * 定义工具管理的标准操作
 * 接口定义"做什么"，实现类负责"怎么做"
 *
 * 【为什么要定义接口？】
 * 1. 符合面向接口编程原则
 * 2. 便于单元测试时创建 Mock 实现
 * 3. 解耦 Controller 和具体实现
 * 4. 如果未来需要替换实现（如从本地工具改为远程服务），
 *    只需修改 @Autowired 注入的 Bean，不影响 Controller
 * @author 17644
 */

public interface ToolService {
    /**
     * 获取所有可用工具列表
     *
     * 【设计目的】
     * 返回所有已注册的工具，供前端展示
     *
     * @return 工具列表
     */
    List<ToolResponse> getToolList();

    /**
     * 执行指定工具
     *
     * 【设计目的】
     * 根据工具名称和参数执行工具，返回执行结果
     *
     * 【为什么不直接返回 String？】
     * 返回 ExecuteToolResponse 对象，包含：
     * - toolName：工具名称
     * - result：执行结果
     * - executionTime：执行耗时（便于性能监控）
     * - success：是否成功
     * - errorMessage：错误信息（如果失败）
     *
     * 【为什么不把执行耗时放在 execute() 方法内？】
     * 保持 execute() 方法的纯粹性，只负责执行
     * 在 Service 层统一计时，便于统一处理
     *
     * @param request 执行工具请求（包含工具名和参数）
     * @return 执行结果
     */
    ExecuteToolResponse executeTool(ExecuteToolRequest request);
}
