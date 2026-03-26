# Function Calling 实现方案

> 版本：v1.0.0
> 更新日期：2026-03-25
> 状态：待实现

---

## 一、设计目标

将系统从「独立工具接口」升级为「真正的 Agent」，实现：

1. **智能意图识别**：AI 自动判断何时需要调用工具
2. **自动工具调用**：无需用户手动指定，AI 自动执行
3. **多轮交互能力**：工具结果自动反馈给 AI，AI 继续处理
4. **工具动态管理**：Java 代码定义行为，数据库管理元数据

---

## 二、架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户请求                                 │
│                   "北京今天天气怎么样？"                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ChatController                              │
│                      POST /api/v1/chat                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ChatService                                 │
│                    chatWithTools()                               │
└─────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            │                                   │
            ▼                                   ▼
┌───────────────────────┐           ┌───────────────────────┐
│   DeepSeekClient      │           │  FunctionCallingService│
│   chatWithTools()     │           │  getToolDefinitions()  │
└───────────────────────┘           └───────────────────────┘
            │                                   │
            │         tools参数                 │
            └───────────────┬───────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       DeepSeek API                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                            ...（循环调用）
```

### 2.2 工具管理架构（混合方案）

```
┌─────────────────────────────────────────────────────────────────┐
│                          工具管理架构                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────┐      ┌─────────────────┐                  │
│   │  ToolRegistry   │◄─────│   数据库 tool    │                  │
│   │  (内存注册中心)  │      │     表          │                  │
│   └────────┬────────┘      └─────────────────┘                  │
│            │                                                       │
│            ▼                                                       │
│   ┌─────────────────────────────────────────┐                   │
│   │           ToolController                 │                   │
│   │   GET  /api/v1/tools      → 返回启用的工具列表  │                   │
│   │   POST /api/v1/tools/execute → 执行指定工具    │                   │
│   └─────────────────────────────────────────┘                   │
│                                                                  │
│   工具来源：                                                      │
│   ┌─────────────────────────────────────────┐                   │
│   │  WeatherTool.java  → @Component 注册到 Spring                 │
│   │  CalculatorTool.java → @Component 注册到 Spring               │
│   │  NewsTool.java     → @Component 注册到 Spring                   │
│   └─────────────────────────────────────────┘                   │
│                                                                  │
│   工具元数据（描述、参数）从数据库读取                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、工具管理设计

### 3.1 数据库表结构（tool 表）

```sql
CREATE TABLE tool (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工具ID',
    tool_name       VARCHAR(50) NOT NULL UNIQUE COMMENT '工具唯一标识',
    display_name    VARCHAR(100) COMMENT '显示名称',
    description     TEXT COMMENT 'AI描述（决定何时调用）',
    parameters      JSON COMMENT '参数定义JSON',
    class_name      VARCHAR(255) COMMENT '实现类全名',
    status          TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.2 分层职责

| 层级 | 职责 | 示例 |
|------|------|------|
| **Java 代码** | 定义工具行为、核心执行逻辑 | `WeatherTool.execute()` |
| **数据库** | 存储元数据（描述、参数、状态） | `description`、`parameters`、`status` |
| **ToolRegistry** | 内存注册中心，运行时管理 | 注册/获取/列表工具 |
| **ToolController** | 对外 API 接口 | `/tools`、`/tools/execute` |

### 3.3 添加工具流程

```
┌─────────────────────────────────────────────────────────────────┐
│                         添加新工具流程                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  步骤1：编写 Java 类                                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ @Component                                                   ││
│  │ public class NewsTool extends AbstractTool {                  ││
│  │     @Override                                                ││
│  │     public String getName() { return "news"; }               ││
│  │                                                              ││
│  │     @Override                                                ││
│  │     public String getDescription() {                         ││
│  │         return "查询最新新闻资讯...";                         ││
│  │     }                                                        ││
│  │                                                              ││
│  │     @Override                                                ││
│  │     public String execute(Map<String, Object> params) {     ││
│  │         // 调用外部API获取新闻                                ││
│  │     }                                                        ││
│  │ }                                                            ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  步骤2：自动注册到 ToolRegistry（通过 @PostConstruct 或构造函数） │
│                                                                  │
│  步骤3：在数据库添加记录                                          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ INSERT INTO tool (tool_name, display_name, description,       ││
│  │   parameters, class_name, status)                            ││
│  │ VALUES ('news', '新闻查询', '查询最新新闻...',                ││
│  │   '{"topic": {"type": "string"}}',                           ││
│  │   'com.jujiu.agent.tool.impl.NewsTool', 1);                 ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.4 启动时同步逻辑

```java
@Component
@Slf4j
public class ToolRegistryInitializer {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolRepository toolRepository;

    /**
     * Spring 启动完成后，同步数据库配置到注册中心
     */
    @PostConstruct
    public void syncToolsFromDatabase() {
        log.info("[工具初始化] 开始同步数据库工具配置...");

        // 1. 查询所有启用的工具
        List<Tool> enabledTools = toolRepository.lambdaQuery()
                .eq(Tool::getStatus, 1)
                .list();

        for (Tool tool : enabledTools) {
            // 2. 检查是否已注册
            if (!toolRegistry.hasTool(tool.getToolName())) {
                log.warn("[工具初始化] 数据库中有工具但未注册: {}", tool.getToolName());
            } else {
                log.info("[工具初始化] 工具已注册: {}", tool.getToolName());
            }
        }

        log.info("[工具初始化] 同步完成，已注册工具数量: {}", toolRegistry.getToolCount());
    }
}
```

---

## 四、Function Calling 核心实现

### 4.1 核心流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     Function Calling 流程                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 用户发送消息 "北京今天天气怎么样？"                            │
│                                                                  │
│  2. ChatService 调用 FunctionCallingService.chatWithTools()      │
│                                                                  │
│  3. 构建请求（messages + tools）发送给 DeepSeek                  │
│                                                                  │
│  4. DeepSeek 返回（判断需要调用工具）：                            │
│     {                                                            │
│       "tool_calls": [{                                           │
│         "id": "call_001",                                        │
│         "function": {                                            │
│           "name": "weather",                                     │
│           "arguments": "{\"city\":\"北京\"}"                     │
│         }                                                        │
│       }]                                                         │
│     }                                                            │
│                                                                  │
│  5. 执行工具，获取结果                                            │
│     "北京当前天气：晴，温度18℃，湿度45%"                          │
│                                                                  │
│  6. 将结果作为 tool 角色消息加入上下文                             │
│     {"role": "tool", "tool_call_id": "call_001",                │
│      "content": "北京当前天气：晴，温度18℃..."}                   │
│                                                                  │
│  7. 再次发送给 DeepSeek                                          │
│                                                                  │
│  8. DeepSeek 返回最终结果（无需继续调用工具）                      │
│     "根据查询结果，北京今天天气晴朗，气温18℃..."                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 关键类设计

#### 4.2.1 ToolDefinition（工具定义 DTO）

```java
// 用途：将工具转换为 DeepSeek API 格式的 tools 参数

@Data
public class ToolDefinition {
    private String type = "function";
    private Function function;

    @Data
    public static class Function {
        private String name;        // 工具名称
        private String description;   // 工具描述（AI靠这个判断）
        private Parameters parameters; // 参数定义
    }

    @Data
    public static class Parameters {
        private String type = "object";
        private Map<String, Property> properties;
        private List<String> required;
    }

    @Data
    public static class Property {
        private String type;
        private String description;
    }
}
```

#### 4.2.2 ToolCallDTO（工具调用记录 DTO）

```java
// 用途：解析 DeepSeek API 返回的 tool_calls

@Data
public class ToolCallDTO {
    private String id;           // 调用ID
    private String type;         // 类型（固定为function）
    private Function function;   // 函数信息

    @Data
    public static class Function {
        private String name;     // 函数名
        private String arguments; // JSON参数字符串
    }
}
```

#### 4.2.3 FunctionCallingService（核心服务）

```java
// 职责：
// 1. 将工具转换为 DeepSeek 格式的 tools 参数
// 2. 处理工具调用循环
// 3. 管理对话上下文

@Service
public class FunctionCallingService {

    public DeepSeekResult chatWithTools(List<DeepSeekMessage> messages, DeepSeekClient client) {
        List<ToolDefinition> toolDefinitions = getToolDefinitions();

        if (toolDefinitions.isEmpty()) {
            return client.chat(messages); // 没有工具，普通对话
        }

        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            iteration++;

            // 发送请求（带上 tools 定义）
            DeepSeekResult result = client.chatWithTools(messages, toolDefinitions);

            // 检查是否有工具调用
            List<ToolCallDTO> toolCalls = result.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return result; // 没有工具调用，返回结果
            }

            // 并行执行所有工具调用
            for (ToolCallDTO toolCall : toolCalls) {
                String toolResult = executeTool(toolCall);
                // 将结果作为新消息加入对话
                messages.add(DeepSeekMessage.toolMessage(toolCall.getId(), toolResult));
            }
        }

        throw new RuntimeException("工具调用超过最大次数限制");
    }

    // 将 AbstractTool 转换为 ToolDefinition
    private ToolDefinition convertToToolDefinition(AbstractTool tool) {
        ToolDefinition definition = new ToolDefinition();
        definition.setType("function");

        ToolDefinition.Function function = new ToolDefinition.Function();
        function.setName(tool.getName());
        function.setDescription(tool.getDescription());
        function.setParameters(tool.getParameters());

        definition.setFunction(function);
        return definition;
    }
}
```

### 4.3 消息角色扩展

```java
// DeepSeekMessage 需要支持 tool 角色

public static class DeepSeekMessage {

    // 工具消息（用于返回工具执行结果）
    public static DeepSeekMessage toolMessage(String toolCallId, String content) {
        return new DeepSeekMessage("tool", content, toolCallId, null);
    }

    // tool 角色消息的 JSON 格式：
    // {
    //   "role": "tool",
    //   "tool_call_id": "call_001",
    //   "content": "北京当前天气：晴..."
    // }
}
```

---

## 五、文件变更清单

### 5.1 新增文件

| 文件路径 | 说明 |
|----------|------|
| `model/dto/tool/ToolDefinition.java` | 工具定义 DTO |
| `model/dto/tool/ToolCallDTO.java` | 工具调用记录 DTO |
| `core/FunctionCallingService.java` | Function Calling 核心服务 |

### 5.2 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `model/dto/deepseek/DeepSeekRequest.java` | 添加 `tools` 字段 |
| `model/dto/deepseek/DeepSeekResponse.java` | 添加 `tool_calls` 解析 |
| `model/dto/deepseek/DeepSeekMessage.java` | 添加 `tool` 角色支持 |
| `client/DeepSeekResult.java` | 添加 `toolCalls` 字段 |
| `client/DeepSeekClient.java` | 添加 `chatWithTools()` 方法 |
| `tool/AbstractTool.java` | 添加 `getParameters()` 方法 |
| `tool/impl/WeatherTool.java` | 实现参数定义 |
| `service/ChatService.java` | 添加 `chatWithTools` 方法 |
| `service/impl/ChatServiceImpl.java` | 实现工具调用逻辑 |
| `core/ToolRegistry.java` | 可选：添加从数据库同步的方法 |

---

## 六、时序图

```
┌────────┐    ┌────────────────┐    ┌────────────────┐    ┌────────────┐
│  用户  │    │ ChatController │    │    ChatService │    │ DeepSeek  │
└───┬────┘    └───────┬────────┘    └───────┬────────┘    └─────┬──────┘
    │                 │                     │                  │
    │ 1.发送消息      │                     │                  │
    │───────────────►│                     │                  │
    │                 │                     │                  │
    │                 │ 2.chatWithTools()  │                  │
    │                 │───────────────────►│                  │
    │                 │                     │                  │
    │                 │                     │ 3.获取工具定义   │
    │                 │                     │────────────────►│
    │                 │                     │    (tools参数)   │
    │                 │                     │                  │
    │                 │                     │ 4.发送请求       │
    │                 │                     │ (messages+tools)│
    │                 │                     │────────────────►│
    │                 │                     │                  │
    │                 │                     │ 5.AI决定调用工具  │
    │                 │                     │◄────────────────│
    │                 │                     │ (tool_calls)     │
    │                 │                     │                  │
    │                 │                     │ 6.执行工具       │
    │                 │                     │────────────────►│
    │                 │                     │                  │
    │                 │                     │ 7.添加tool消息   │
    │                 │                     │ (role=tool)     │
    │                 │                     │                  │
    │                 │                     │ 8.循环请求       │
    │                 │                     │────────────────►│
    │                 │                     │                  │
    │                 │                     │ 9.返回最终结果   │
    │                 │◄───────────────────│                  │
    │◄────────────────│                     │                  │
    │ 10.返回结果      │                     │                  │
```

---

## 七、API 格式示例

### 7.1 DeepSeek 请求格式（带 tools）

```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "user", "content": "北京今天天气怎么样？"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "weather",
        "description": "查询指定城市的当前天气情况...",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {
              "type": "string",
              "description": "城市名称，如'北京'、'Shanghai'"
            }
          },
          "required": ["city"]
        }
      }
    }
  ]
}
```

### 7.2 DeepSeek 响应格式（tool_calls）

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "id": "call_abc123",
            "type": "function",
            "function": {
              "name": "weather",
              "arguments": "{\"city\":\"北京\"}"
            }
          }
        ]
      },
      "finish_reason": "tool_calls"
    }
  ]
}
```

### 7.3 工具结果消息格式

```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "北京当前天气：晴，温度18℃，湿度45%，风速2.5m/s"
}
```

---

## 八、安全与边界处理

### 8.1 循环次数限制

```java
private static final int MAX_ITERATIONS = 10; // 防止无限循环
```

### 8.2 工具执行超时

```java
private static final int TOOL_TIMEOUT_SECONDS = 30;

CompletableFuture.supplyAsync(() -> executeTool(toolCall))
    .get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
```

### 8.3 错误恢复

```java
try {
    return tool.execute(params);
} catch (Exception e) {
    log.error("工具执行异常", e);
    return "错误：" + e.getMessage(); // 返回错误信息，不中断循环
}
```

---

## 九、实现检查清单

- [ ] 新增 `ToolDefinition.java`
- [ ] 新增 `ToolCallDTO.java`
- [ ] 新增 `FunctionCallingService.java`
- [ ] 修改 `DeepSeekMessage.java` 添加 tool 角色
- [ ] 修改 `DeepSeekRequest.java` 添加 tools 字段
- [ ] 修改 `DeepSeekResponse.java` 解析 tool_calls
- [ ] 修改 `DeepSeekResult.java` 添加 toolCalls 字段
- [ ] 修改 `DeepSeekClient.java` 添加 chatWithTools() 方法
- [ ] 修改 `AbstractTool.java` 添加 getParameters() 方法
- [ ] 修改 `WeatherTool.java` 实现参数定义
- [ ] 修改 `ChatService.java` 添加 chatWithTools() 方法
- [ ] 修改 `ChatServiceImpl.java` 实现工具调用逻辑
- [ ] 测试：普通对话（无工具调用）
- [ ] 测试：单次工具调用
- [ ] 测试：多次工具调用（多轮）
- [ ] 测试：工具执行超时处理
- [ ] 测试：工具执行异常处理

---

## 十、相关文档

- [项目状态快照](./项目状态快照.md)
- [项目开发全记录](./项目开发全记录.md)
