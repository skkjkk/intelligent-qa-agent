# 智能问答Agent系统

> 基于Spring Boot的智能问答Agent系统后端API，支持Function Calling、工具调用、多轮对话

## 项目信息

| 项目 | 说明 |
|------|------|
| **项目名称** | Intelligent QA Agent |
| **开发者** | 居九 |
| **开发时间** | 2026-03-19 开始 |
| **项目路径** | `D:\IdeaCode\intelligent-qa-agent` |

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.4.4 | 核心框架 |
| MyBatis-Plus | 3.5.5 | ORM框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 7.2+ | 缓存/限流 |
| Spring Security | 6.x | 安全认证 |
| JWT | - | Token认证 |
| SpringDoc OpenAPI | 2.8.5 | API文档 |
| Spring WebFlux | - | 响应式编程 |
| WebClient | - | HTTP客户端 |

## 功能特性

### 已完成

- **用户认证模块**
  - 用户注册/登录/登出
  - JWT Token认证（accessToken + refreshToken）
  - 登录失败次数限制（5次/10分钟）
  - Token黑名单机制
  - 登录日志记录

- **对话管理模块**
  - 会话创建/查询/删除
  - 消息发送/查询/删除
  - 多轮对话上下文管理
  - 流式响应（SSE + WebFlux）
  - 会话标题自动生成
  - Token用量统计

- **工具接口模块**
  - 工具注册中心（ToolRegistry）
  - 天气查询（OpenWeatherMap API）
  - 通用工具执行接口

- **Function Calling模块**（设计完成，待实现）
  - Agent智能工具调用
  - 工具自动识别与执行
  - 多轮工具调用循环

### 待开发

- 记忆管理模块
- 工作流模块
- 单元测试

## 项目结构

```
intelligent-qa-agent/
├── docs/                              # 项目文档
│   ├── requirements/                  # 需求文档（8个）
│   ├── design/                        # 设计文档（3个）
│   ├── guide/                         # 学习指南（5个）
│   ├── 项目状态快照.md
│   ├── 项目开发全记录.md
│   └── Function Calling实现方案.md
├── sql/                               # SQL脚本
│   └── init.sql                       # 数据库初始化脚本
├── src/main/java/com/jujiu/agent/
│   ├── AgentApplication.java          # 启动类
│   ├── config/                        # 配置类
│   │   ├── SecurityConfig.java        # Security配置
│   │   ├── SwaggerConfig.java         # Swagger配置
│   │   ├── WebClientConfig.java      # WebClient配置
│   │   └── DeepSeekProperties.java   # DeepSeek配置
│   ├── controller/                    # 控制器
│   │   ├── AuthController.java        # 认证接口（9个）
│   │   ├── ChatController.java        # 对话接口（5个）
│   │   └── ToolController.java       # 工具接口（2个）
│   ├── service/                       # 业务逻辑
│   │   ├── AuthService.java          # 认证服务
│   │   ├── ChatService.java          # 对话服务
│   │   └── impl/                     # 服务实现
│   ├── repository/                    # 数据访问
│   │   ├── UserRepository.java
│   │   ├── LoginLogRepository.java
│   │   ├── SessionRepository.java
│   │   ├── MessageRepository.java
│   │   └── ToolRepository.java
│   ├── model/                         # 数据模型
│   │   ├── entity/                    # 实体类
│   │   ├── dto/deepseek/             # DeepSeek相关DTO
│   │   ├── dto/request/              # 请求DTO
│   │   └── dto/response/             # 响应DTO
│   ├── tool/                          # 工具模块
│   │   ├── AbstractTool.java         # 工具抽象基类
│   │   ├── ToolRegistry.java         # 工具注册中心
│   │   └── impl/                     # 工具实现
│   │       └── WeatherTool.java      # 天气查询工具
│   ├── security/                      # 安全模块
│   │   ├── JwtTokenProvider.java     # JWT工具类
│   │   └── JwtAuthenticationFilter.java # JWT过滤器
│   ├── common/                        # 公共模块
│   │   ├── result/                   # 统一响应
│   │   └── exception/                # 异常处理
│   └── client/                        # API客户端
│       ├── DeepSeekClient.java       # DeepSeek客户端
│       └── DeepSeekResult.java       # 调用结果
├── src/main/resources/
│   └── application.yml               # 配置文件
└── pom.xml                            # Maven配置
```

## 数据库表

| 表名 | 说明 |
|------|------|
| `user` | 用户表 |
| `login_log` | 登录日志表 |
| `session` | 会话表 |
| `message` | 消息表 |
| `tool` | 工具表 |
| `refresh_token` | 刷新Token表 |
| `token_blacklist` | Token黑名单表 |

> 详见 `sql/init.sql`

## API接口

启动项目后访问Swagger UI：
```
http://localhost:8080/swagger-ui.html
```

### 认证接口 `/api/v1/auth`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/login` | POST | 用户登录 |
| `/register` | POST | 用户注册 |
| `/refresh` | POST | 刷新Token |
| `/logout` | POST | 用户登出 |
| `/password` | PUT | 修改密码 |
| `/me` | GET | 获取当前用户信息 |

### 对话接口 `/api/v1/chat`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sessions` | GET | 获取会话列表 |
| `/sessions` | POST | 创建新会话 |
| `/sessions/{sessionId}` | GET | 获取会话详情 |
| `/sessions/{sessionId}` | DELETE | 删除会话 |
| `/send` | POST | 发送消息（非流式） |
| `/send/stream` | POST | 发送消息（流式SSE） |

### 工具接口 `/api/v1/tools`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/` | GET | 获取工具列表 |
| `/execute` | POST | 执行指定工具 |

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 7.2+

### 2. 数据库初始化

```sql
CREATE DATABASE intelligent_qa_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 执行 sql/init.sql 脚本创建表结构
```

### 3. 配置

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/intelligent_qa_agent
    username: root
    password: your_password
  redis:
    host: localhost
    port: 6379

jwt:
  secret: your-jwt-secret-key-change-in-production
  access-expiration: 86400000    # 24小时
  refresh-expiration: 604800000   # 7天

deepseek:
  api-key: your-deepseek-api-key
  base-url: https://api.deepseek.com
  model: deepseek-chat

weather:
  api:
    key: your-openweathermap-api-key
    url: https://api.openweathermap.org
```

### 4. 启动项目

```bash
# 进入项目目录
cd D:\IdeaCode\intelligent-qa-agent

# 编译打包
mvn clean package -DskipTests

# 启动
mvn spring-boot:run

# 或直接运行jar
java -jar target/agent-0.0.1-SNAPSHOT.jar
```

### 5. 访问API文档

```
http://localhost:8080/swagger-ui.html
```

## 开发指南

### 添加新工具

1. 创建工具类继承 `AbstractTool`：

```java
@Component
public class NewsTool extends AbstractTool {

    public NewsTool(ToolRegistry toolRegistry) {
        toolRegistry.register(this);  // 注册到中心
    }

    @Override
    public String getName() {
        return "news";
    }

    @Override
    public String getDescription() {
        return "查询最新新闻资讯...";
    }

    @Override
    public String execute(Map<String, Object> params) {
        // 实现工具逻辑
        return "新闻内容...";
    }
}
```

2. 在数据库 `tool` 表添加记录：

```sql
INSERT INTO tool (tool_name, display_name, description, class_name, status)
VALUES ('news', '新闻查询', '查询最新新闻...', 'com.jujiu.agent.tool.impl.NewsTool', 1);
```

### Function Calling（Agent模式）

详见 `docs/Function Calling实现方案.md`

## 相关文档

| 文档 | 说明 |
|------|------|
| `docs/项目状态快照.md` | 项目当前状态 |
| `docs/项目开发全记录.md` | 开发历史记录 |
| `docs/Function Calling实现方案.md` | Agent实现方案 |
| `docs/requirements/` | 需求文档 |
| `docs/design/` | 设计文档 |
| `docs/guide/` | 学习指南 |

## 开发进度

| 模块 | 状态 | 完成度 |
|------|------|--------|
| 用户认证模块 | 完成 | 100% |
| 对话管理模块 | 完成 | 100% |
| 工具接口模块 | 完成 | 100% |
| Function Calling | 设计完成 | 待实现 |
| 记忆管理模块 | 待开发 | 0% |
| 工作流模块 | 待开发 | 0% |
| 单元测试 | 待开发 | 0% |

## 许可证

Apache License 2.0
