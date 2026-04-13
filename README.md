# 智能问答Agent系统

> 基于 `Spring Boot 3` 的智能问答 Agent 后端，支持 `JWT` 认证、会话管理、同步聊天、`SSE` 流式响应、`Function Calling`、工具调用与知识库 `RAG`。

## 文档导航

- 文档总入口：`docs/README.md`
- 需求基线：`docs/02-requirements/需求规格说明书.md`
- 架构基线：`docs/03-architecture/系统架构设计.md`
- 数据库设计：`docs/03-architecture/数据库设计.md`
- RAG 架构：`docs/03-architecture/知识库与RAG架构设计.md`
- RAG 当前完成情况：`docs/03-architecture/知识库与RAG当前完成情况.md`
- 阶段性收尾清单：`docs/01-overview/知识库与Agent阶段性收尾清单.md`
- 接口基线：`docs/04-api/API接口定义文档.md`
- 运维检查：`docs/05-operations/环境配置检查清单.md`

## 项目简介

这是一个围绕“认证 -> 会话/消息 -> 大模型调用 -> 工具执行 -> 回复持久化 -> 返回客户端”主链路构建的后端项目。

当前项目已经从基础对话 Agent 后端演进为支持知识库增强的 Agent 系统，除原有聊天与工具调用能力外，还支持文档上传、异步解析、分块、向量索引、独立知识库问答、流式知识库问答、`knowledge_base` 工具调用、显式知识增强聊天、统计、健康检查与索引治理能力。

当前代码已经实现：

- 基于 `Spring Security + JWT` 的登录认证、登出与刷新机制
- 会话创建、分页、详情查询与删除
- 同步聊天与 `SSE` 流式聊天
- 基于 `DeepSeek` 的 `Function Calling`
- 工具注册、数据库配置加载与工具执行
- Redis 限流、登录状态辅助控制与 Token 黑名单支持
- 文档上传、MinIO 存储、Kafka 异步解析与分块入库
- 基于 `embedding-3` 的向量化与 Redis 缓存
- 基于 `Elasticsearch` 的分块索引与混合检索骨架
- 独立知识库同步 / 流式问答、问答历史与反馈闭环
- 知识库概览统计、文档统计、问答统计与健康检查
- 单文档索引、批量索引、单文档重建索引与失败索引重建
- 批量索引与批量重建接口支持返回结构化执行摘要
- `knowledge_base` 工具接入与聊天显式知识增强
- 文档级 ACL 正式生效

## 项目信息

| 项目 | 说明 |
|------|------|
| **项目名称** | Intelligent QA Agent |
| **项目路径** | `D:\IdeaCode\intelligent-qa-agent` |
| **后端技术栈** | Java 17 + Spring Boot 3.4.x |
| **主要能力** | 认证、对话、流式响应、Function Calling、工具执行、知识库 RAG |

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.4.4 | 核心框架 |
| Spring Security | 6.x | 安全认证 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 7.x | 缓存、限流、状态辅助 |
| Spring WebFlux | - | WebClient 与流式能力 |
| SseEmitter | - | SSE 流式响应 |
| JWT | 0.12.5 | Token 认证 |
| SpringDoc OpenAPI | 2.8.6 | API 文档 |
| Hutool | 5.8.25 | 工具库 |
| Elasticsearch | 8.x | 知识库分块索引与向量检索 |
| Kafka | - | 文档异步处理流水线 |
| MinIO | - | 知识库原始文件存储 |
| PDFBox / POI / Jsoup | - | 文档解析 |

## 核心功能

### 认证模块

- 用户注册、登录、登出
- Access Token / Refresh Token
- 登录失败次数限制
- Token 黑名单
- 用户信息获取与密码修改
- 登录日志记录

### 对话模块

- 会话创建、分页、详情、删除
- 同步聊天
- `SSE` 流式聊天
- 多轮上下文管理
- 会话标题生成
- Token 用量记录
- 显式知识库增强聊天

### Function Calling 模块

- 向模型传入工具定义
- 解析 `tool_calls`
- 执行本地工具
- 将工具执行结果继续回传模型补全最终答案

### 工具模块

当前代码中的工具实现包括：

- `WeatherTool`：天气查询
- `WebSearchTool`：网页搜索
- `TranslatorTool`：翻译
- `TimeTool`：时间查询
- `CalculatorTool`：计算器
- `KnowledgeBaseTool`：知识库检索问答

### 知识库与 RAG 模块

- 文档上传、详情、列表、状态查询与删除
- `txt / md / pdf / docx / html` 文档解析
- Kafka 驱动的异步处理链路
- 分块入库、Embedding 向量化与 Elasticsearch 索引
- 独立知识库同步问答与流式问答
- 问答历史查询与反馈提交
- 概览统计、文档统计、问答统计与健康检查
- 单文档索引、批量索引、重建索引与失败索引恢复
- 聊天显式知识增强
- 文档 ACL、检索 ACL 过滤与 ACL 生效后的 Agent 复用

### ACL 当前状态

- 已提供 `kb_document_acl` 文档访问控制表
- 已支持 `READ / MANAGE / SHARE` 三类权限
- 文档详情、列表、状态、删除、单文档索引、单文档重建索引已接入 ACL 判定
- 批量索引、批量重建已纳入 ACL 可管理文档语义
- 检索服务已按“当前用户可读文档集合”执行，向量检索与关键词检索统一带 ACL 过滤
- `kb/query`、`kb/query/stream`、`knowledge_base` 工具与聊天显式知识增强统一复用 ACL 生效后的检索结果
- 已支持 `USER / GROUP` 两类主体
- 已支持 `PRIVATE / PUBLIC / GROUP_SHARED` 三类可见性
- 已支持文档 ACL 管理、共享组管理、组管理与 ACL 审计日志
- 当前 ACL 以文档级知识库共享为主，不扩展到 chunk 级权限

## 系统架构

```mermaid
flowchart TD
    A[前端 / 客户端] --> B[Controller]
    B --> C[Spring Security / JWT]
    C --> D[Service]
    D --> E[Repository]
    E --> F[(MySQL)]
    D --> G[(Redis)]
    D --> H[DeepSeekClient]
    D --> I[ToolRegistry]
    I --> J[AbstractTool 实现]
    D --> K[KnowledgeBase / RagService]
    K --> L[(Elasticsearch)]
    K --> M[(MinIO)]
    K --> N[(Kafka)]
```

### 分层说明

- `controller`：HTTP 入口、参数接收、结果返回
- `service`：业务编排、聊天流程、Function Calling、工具执行
- `repository`：基于 `MyBatis-Plus` 的数据访问
- `client`：第三方模型服务调用封装
- `tool`：工具抽象、工具注册、工具实现
- `security`：JWT 认证过滤与令牌处理
- `service.kb`：知识库文档处理、检索、RAG、统计与健康检查
- `parser / storage / mq / search`：知识库解析、存储、异步流水线与索引能力

## 核心流程

### 聊天主流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Controller as ChatController
    participant Service as ChatServiceImpl
    participant FC as FunctionCallingServiceImpl
    participant DS as DeepSeekClient
    participant Tool as ToolRegistry / Tool
    participant DB as MySQL

    Client->>Controller: 发送聊天请求
    Controller->>Service: sendMessage / sendMessageStream
    Service->>Service: 校验会话、限流、组装上下文
    Service->>DB: 保存用户消息
    Service->>FC: 发起带工具对话
    FC->>DS: 请求模型
    DS-->>FC: 返回回复或 tool_calls
    FC->>Tool: 执行工具
    Tool-->>FC: 返回工具结果
    FC->>DS: 带工具结果继续补全
    DS-->>FC: 返回最终答案
    FC-->>Service: 返回结果
    Service->>DB: 保存 AI 消息并更新会话
    Service-->>Client: 返回同步结果或 SSE 事件流
```

### 工具调用流程

```mermaid
flowchart TD
    A[模型返回 tool_calls] --> B[FunctionCallingServiceImpl]
    B --> C[ToolRegistry 查找工具实现]
    C --> D[具体工具执行]
    D --> E[生成 tool message]
    E --> F[再次请求模型]
    F --> G[返回最终回复]
```

### 知识库处理流程

```mermaid
flowchart TD
    A[上传文档] --> B[MinIO 存储原始文件]
    B --> C[写入 kb_document]
    C --> D[发送 Kafka 事件]
    D --> E[DocumentProcessConsumer]
    E --> F[DocumentParser 提取文本]
    F --> G[ChunkService 分块]
    G --> H[写入 kb_chunk]
    H --> I[EmbeddingService 向量化]
    I --> J[Elasticsearch 建索引]
```

### 知识库问答流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Controller as KnowledgeQueryController
    participant Rag as RagServiceImpl
    participant Search as VectorSearchServiceImpl
    participant DS as DeepSeekClient
    participant DB as MySQL

    Client->>Controller: /api/v1/kb/query
    Controller->>Rag: query(...)
    Rag->>Search: search(...)
    Search-->>Rag: TopK 分块结果
    Rag->>DS: 携带上下文生成答案
    DS-->>Rag: 返回答案
    Rag->>DB: 写入 kb_query_log / kb_retrieval_trace
    Rag-->>Controller: answer + citations
```

## 项目结构

```text
intelligent-qa-agent/
├── docs/                              # 项目文档
├── sql/                               # SQL 脚本
├── src/main/java/com/jujiu/agent/
│   ├── AgentApplication.java          # 启动类
│   ├── client/                        # DeepSeek 客户端封装
│   ├── common/                        # 公共结果与异常
│   ├── config/                        # 配置类
│   ├── controller/                    # 控制器
│   ├── model/                         # DTO / Entity
│   ├── mq/                            # Kafka 生产消费
│   ├── parser/                        # 文档解析器
│   ├── repository/                    # 数据访问层
│   ├── search/                        # ES 索引文档
│   ├── security/                      # JWT 认证相关
│   ├── service/                       # 业务服务
│   ├── storage/                       # MinIO 文件服务
│   └── tool/                          # 工具抽象、注册中心、工具实现
├── src/main/resources/                # 配置文件
└── pom.xml                            # Maven 配置
```

## 数据库表

当前主业务表包括：

- `user`
- `login_log`
- `session`
- `message`
- `tool`
- `kb_document`
- `kb_chunk`
- `kb_query_log`
- `kb_query_feedback`
- `kb_document_process_log`
- `kb_retrieval_trace`

详见：`sql/init.sql` 与 `docs/03-architecture/数据库设计.md`

## API 概览

启动项目后访问 Swagger UI：

```text
http://localhost:8080/swagger-ui.html
```

### 认证接口 `/api/v1/auth`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/login` | POST | 用户登录 |
| `/register` | POST | 用户注册 |
| `/refresh` | POST | 刷新 Token |
| `/logout` | POST | 用户登出 |
| `/me` | GET | 获取当前用户信息 |
| `/password` | POST | 修改密码 |

### 对话接口 `/api/v1/chat`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sessionList` | GET | 获取会话列表 |
| `/sessions` | POST | 创建会话 |
| `/sessions/{sessionId}` | GET | 获取会话详情 |
| `/sessions/{sessionId}` | DELETE | 删除会话 |
| `/send` | POST | 发送消息（非流式） |
| `/send/stream` | POST | 发送消息（SSE 流式） |

> `page` 参数为 `0-based`：`page=0` 表示第一页，后端内部会转换为分页组件使用的页码。

### 工具接口 `/api/v1/tools`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/list` | GET | 获取工具列表 |
| `/execute` | POST | 执行指定工具 |

### 知识库文档接口 `/api/v1/kb/documents`

| 接口 | 方法 | 说明 |
|------|------|------|
| `` | POST | 上传文档 |
| `` | GET | 查询文档列表 |
| `/{documentId}` | GET | 查询文档详情 |
| `/{documentId}/status` | GET | 查询文档处理状态 |
| `/{documentId}` | DELETE | 删除文档 |

### 知识库问答接口 `/api/v1/kb/query`

| 接口 | 方法 | 说明 |
|------|------|------|
| `` | POST | 知识库同步问答 |
| `/stream` | POST | 知识库流式问答 |
| `/history` | GET | 查询问答历史 |
| `/{queryLogId}/feedback` | POST | 提交问答反馈 |

### 知识库统计与健康接口 `/api/v1/kb`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/stats/overview` | GET | 查询知识库概览统计 |
| `/stats/documents` | GET | 查询知识库文档统计 |
| `/stats/queries` | GET | 查询知识库问答统计 |
| `/health` | GET | 知识库健康检查 |

### 知识库索引接口 `/api/v1/kb/index`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/{documentId}` | POST | 手动触发单文档索引 |
| `/pending` | POST | 批量索引当前用户待处理文档，并返回批量执行摘要 |
| `/rebuild/{documentId}` | POST | 重建单文档索引 |
| `/rebuild/failed` | POST | 批量重建当前用户失败索引，并返回批量执行摘要 |

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis
- Elasticsearch 8.x
- Kafka
- MinIO

### 2. 数据库初始化

```sql
CREATE DATABASE intelligent_qa_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后执行：`sql/init.sql`

### 3. 配置说明

项目使用：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

开发环境当前配置要点：

```yaml
deepseek:
  api-key: ${DEEPSEEK_API_KEY:your-key}
  base-url: https://api.deepseek.com
  model: deepseek-chat
  max-context-messages: 50
  max-messages-per-minute: 100
  rate-limit-window-seconds: 60

knowledge-base:
  embedding:
    api-url: https://open.bigmodel.cn/api/paas/v4/embeddings
    api-key: ${KB_EMBEDDING_API_KEY:}
    model: embedding-3
    dimension: 2048
  elasticsearch:
    index-name: kb_chunks_v2

app:
  cors:
    allowed-origins:
      - http://localhost:5173
      - http://127.0.0.1:5173
```

还需要准备的环境变量：

- `DB_PASSWORD`
- `JWT_SECRET`
- `DEEPSEEK_API_KEY`
- `KB_EMBEDDING_API_KEY`
- `KB_MINIO_ENDPOINT`
- `KB_MINIO_ACCESS_KEY`
- `KB_MINIO_SECRET_KEY`
- `AMAP_WEATHER_KEY`（可选）
- `SERPAPI_API_KEY`（可选）
- `BAIDU_TRANSLATE_APP_ID`（可选）
- `BAIDU_TRANSLATE_APP_SECRET`（可选）

### 4. 启动项目

```bash
mvn spring-boot:run
```

或：

```bash
mvn clean package -DskipTests
java -jar target/intelligent-qa-agent-1.0.0.jar
```

### 5. 访问 API 文档

```text
http://localhost:8080/swagger-ui.html
```

## 开发指南

### 添加新工具

按当前项目约定，新增工具时需要：

1. 在 `src/main/java/com/jujiu/agent/tool/impl` 下新增工具实现
2. 继承 `AbstractTool`
3. 实现：
   - `getName()`
   - `getDescription()`
   - `execute()`
   - `getParameters()`
4. 确保数据库 `tool` 表中的配置与代码实现一致
5. 由 `ToolRegistry` 扫描并加载

### Function Calling

当前 Function Calling 设计与实现参考：

- `docs/archive/solutions/Function Calling实现方案.md`

### 知识库与 RAG

当前知识库与 RAG 开发已经完成主链路，包括：

1. 文档上传、异步解析、分块与索引
2. 文档级 ACL 正式生效
3. ACL 授权管理、共享组管理、组管理与审计日志
4. 独立知识库同步 / 流式问答
5. `knowledge_base` 工具接入
6. 聊天显式知识增强
7. 问答历史与反馈闭环
8. 概览统计、文档统计、问答统计与健康检查
9. 单文档重建索引与失败索引批量重建
10. 批量索引与批量重建结果摘要返回

## 项目亮点

- **认证链路完整**：基于 `Spring Security + JWT` 实现登录、刷新、登出与黑名单辅助控制。
- **聊天能力完整**：支持同步聊天与 `SSE` 流式聊天，两条链路共享核心准备流程。
- **Function Calling 闭环**：模型可返回 `tool_calls`，后端完成工具执行并继续补全最终答案。
- **工具体系可扩展**：采用 `AbstractTool + ToolRegistry + 数据库配置` 的组合式设计。
- **知识库能力完整接入 Agent**：支持文档处理、向量索引、独立 KB 问答、工具调用与聊天增强。
- **ACL 已进入主链路**：文档访问、检索召回、工具调用与聊天增强已统一复用 ACL 生效结果。
- **ACL 已具备共享模型**：当前已支持 `USER + GROUP`、`READ + MANAGE + SHARE`、`PRIVATE + PUBLIC + GROUP_SHARED` 的知识库共享语义。
- **知识库运维能力初步完善**：支持 history、feedback、stats、health、rebuild 与批量执行摘要返回。
- **流式链路已收口**：支持聊天流式、知识库流式问答与流式输出缓冲。
- **工程化持续演进**：聊天限流、聊天持久化、公共逻辑抽取与安全收口正在逐步完善。

## 相关文档

| 文档 | 说明 |
|------|------|
| `docs/01-overview/项目状态快照.md` | 项目当前状态 |
| `docs/01-overview/知识库与Agent阶段性收尾清单.md` | 阶段性收尾与下一轮 TODO |
| `docs/03-architecture/系统架构设计.md` | 系统架构设计 |
| `docs/03-architecture/数据库设计.md` | 数据库设计 |
| `docs/03-architecture/知识库与RAG架构设计.md` | 知识库与 RAG 架构设计 |
| `docs/03-architecture/知识库与RAG当前完成情况.md` | 当前 RAG 完成情况 |
| `docs/04-api/API接口定义文档.md` | API 文档 |
| `docs/05-operations/环境配置检查清单.md` | 环境与运维检查 |
| `docs/archive/solutions/Function Calling实现方案.md` | Function Calling 方案 |

## 后续规划

- 继续增强 `knowledge_base` 工具与聊天增强的协同策略
- 继续补 ACL、共享治理与审计链路测试
- 继续细化 ACL 审计动作与共享治理查询能力
- 补充知识库与 Agent 主链路自动化测试
- 继续细化知识库统计、健康检查与索引治理能力
- 持续降低聊天与 Function Calling 主流程复杂度
- 持续同步 README / API / 运维文档与代码现状
