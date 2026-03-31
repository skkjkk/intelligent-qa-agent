// package com.jujiu.agent.service.impl;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.jujiu.agent.model.dto.request.ExecuteToolRequest;
// import com.jujiu.agent.model.dto.response.ExecuteToolResponse;
// import com.jujiu.agent.model.dto.response.ToolResponse;
// import com.jujiu.agent.service.ToolService;
// import com.jujiu.agent.tool.AbstractTool;
// import com.jujiu.agent.tool.ToolRegistry;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;

// import java.util.HashMap;
// import java.util.List;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.when;

// /**
//  * ToolServiceImpl 单元测试
//  *
//  * @author 17644
//  * @version 1.0.0
//  * @since 2026/3/26 21:19
//  */
// @ExtendWith(MockitoExtension.class)
// public class ToolServiceImplTest {
    
// //    @Mock
// //    private ToolRegistry toolRegistry;
// //
// //    @Mock
// //    private ObjectMapper objectMapper;
// //
// //    @Mock
// //    private ToolServiceImpl toolService;
// //    
// //    @BeforeEach
// //    void setUp() {
// //        toolService = new ToolServiceImpl(toolRegistry, objectMapper);
// //    }
// //
// //    @Test
// //    void testGetToolList() {
// //        // 1. 创建mock工具
// //        AbstractTool mockTool1 = mock(AbstractTool.class);
// //        when(mockTool1.getName()).thenReturn("weather");
// //        when(mockTool1.getDescription()).thenReturn("天气查询工具");
// //        
// //        AbstractTool mockTool2 = mock(AbstractTool.class);
// //        when(mockTool2.getName()).thenReturn("calculator");
// //        when(mockTool2.getDescription()).thenReturn("计算器工具");
// //        
// //        AbstractTool mockTool3 = mock(AbstractTool.class);
// //        when(mockTool3.getName()).thenReturn("web_search");
// //        when(mockTool3.getDescription()).thenReturn("网页搜索工具");
// //        
// //        // 2. 创建工具列表
// //        List<AbstractTool> mockToolList = List.of(mockTool1, mockTool2, mockTool3);
// //        
// //        // 3. 调用toolService.getToolList()
// //        when(toolRegistry.getAllTools()).thenReturn(mockToolList);
// //        
// //        // 4. 验证返回的列表不为空
// //        List<ToolResponse> toolList = toolService.getToolList();
// //        assertNotNull(toolList);
// //        assertEquals(3, toolList.size());
// //    }
// //    
// //    @Test
// //    void testExecuteToolSuccess() {
// //        // 1. 创建ExecuteToolRequest
// //        ExecuteToolRequest request = new ExecuteToolRequest();
// //        request.setToolName("weather");
// //        request.setParameters(new HashMap<>());
// //        
// //        // 2. 创建Mock工具，设置execute()返回结果
// //        AbstractTool mockTool = mock(AbstractTool.class);
// //        when(mockTool.execute(any())).thenReturn("晴天", "25度");
// //        
// //        // 3. 设置toolRegistry返回这个Mock工具
// //        when(toolRegistry.getTool("weather")).thenReturn(mockTool);
// //        
// //        // 4. 调用toolService.executeTool()
// //        ExecuteToolResponse response = toolService.executeTool(request);
// //
// //        // 5. 验证返回success=true，result不为空
// //        assertTrue(response.isSuccess());
// //        assertNotNull(response.getResult());
// //    }
// //    @Test
// //    void testExecuteToolNotFound() {
// //        // 1. 创建请求对象
// //        ExecuteToolRequest request = new ExecuteToolRequest();
// //        request.setToolName("unknown_tool");
// //        request.setParameters(new HashMap<>());
// //        
// //        // 2. 设置mock行为：当调用toolRegistry.getTool("unknown_tool")时，返回null
// //        when(toolRegistry.getTool("unknown_tool")).thenReturn(null);
// //        
// //        // 3. 调用toolService.executeTool()
// //        ExecuteToolResponse response = toolService.executeTool(request);
// //
// //        // 4. 验证返回结果
// //        assertFalse(response.isSuccess());
// //        // 5. 验证错误信息包含"工具不存在"
// //        assertTrue(response.getErrorMessage().contains("工具不存在"));
// //    }
// //
// //    @Test
// //    void testExecuteToolException() {
// //   
// //        // 1. 创建ExecuteToolRequest
// //        ExecuteToolRequest request = new ExecuteToolRequest();
// //        request.setToolName("weather");
// //        request.setParameters(new HashMap<>());
// //        // 2. 创建Mock工具，设置execute()抛出异常
// //        AbstractTool mockTool = mock(AbstractTool.class);
// //        when(mockTool.execute(any())).thenThrow(new RuntimeException("模拟异常"));
// //        // 3. 设置toolRegistry.getTool()返回这个工具
// //        when(toolRegistry.getTool("weather")).thenReturn(mockTool);
// //        // 4. 调用toolService.executeTool()
// //        ExecuteToolResponse response = toolService.executeTool(request);
// //        // 5. 验证返回success=false，errorMessage包含异常信息
// //        assertFalse(response.isSuccess());
// //        assertTrue(response.getErrorMessage().contains("模拟异常"));
// //    }
// }
