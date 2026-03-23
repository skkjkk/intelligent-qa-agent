# 智能问答Agent系统

> 基于Spring Boot的智能问答Agent系统后端API

## 项目信息

- **项目名称**：Intelligent QA Agent
- **开发者**：居九
- **开发时间**：2026-03-19 开始
- **技术栈**：Java 17 + Spring Boot 3.2 + MyBatis + MySQL + Redis

## 功能特性

- ✅ 用户认证授权（JWT）
- ✅ 智能对话（集成DeepSeek API）
- ✅ 多轮对话支持
- ✅ 工具调用框架
- ✅ 对话记忆管理
- ✅ 工作流编排
- ✅ RESTful API
- ✅ Swagger API文档

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 7.2+

### 启动步骤

1. 创建数据库
```sql
CREATE DATABASE intelligent_qa_agent;
```

2. 配置数据库和Redis
编辑 `src/main/resources/application.yml`

3. 启动项目
```bash
mvn spring-boot:run
```

4. 访问Swagger UI
```
http://localhost:8080/swagger-ui.html
```

## 项目结构

```
intelligent-qa-agent/
├── docs/                   # 项目文档
│   ├── requirements/       # 需求文档
│   └── design/            # 设计文档
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/jujiu/agent/
│   │   │       ├── config/      # 配置类
│   │   │       ├── controller/  # 控制器
│   │   │       ├── service/     # 业务逻辑
│   │   │       ├── repository/  # 数据访问
│   │   │       └── model/       # 数据模型
│   │   └── resources/
│   │       └── application.yml  # 配置文件
│   └── test/              # 测试代码
└── pom.xml                # Maven配置
```

## API文档

启动项目后访问：http://localhost:8080/swagger-ui.html

## 开发进度

- [x] 项目初始化
- [ ] 用户认证模块
- [ ] 对话管理模块
- [ ] 工具调用模块
- [ ] 记忆管理模块
- [ ] 工作流模块

## 许可证

Apache 2.0
