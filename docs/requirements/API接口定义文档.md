# API接口定义文档
# 智能问答Agent系统

**文档版本**：v1.0
**创建日期**：2026-03-19
**创建人**：居九
**优先级**：P0（必须实现）

---

## 1. 接口规范

### 1.1 基础规范

**Base URL**：`http://localhost:8080/api/v1`

**请求格式**：
- Content-Type: application/json
- 字符编码: UTF-8

**认证方式**：
- Header: `Authorization: Bearer {token}`

**统一响应格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-03-19T10:00:00Z"
}
```

---

## 2. 认证接口

### 2.1 用户注册
**POST** `/auth/register`

**请求参数**：
```json
{
  "username": "jujiu",
  "password": "Password123",
  "email": "jujiu@example.com",
  "nickname": "居九"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": "1001",
    "username": "jujiu",
    "createdAt": "2026-03-19T10:00:00Z"
  }
}
```

---

### 2.2 用户登录
**POST** `/auth/login`

**请求参数**：
```json
{
  "username": "jujiu",
  "password": "Password123"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400,
    "tokenType": "Bearer"
  }
}
```

---

### 2.3 Token刷新
**POST** `/auth/refresh`

**请求参数**：
```json
{
  "refreshToken": "string"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "刷新成功",
  "data": {
    "accessToken": "string",
    "expiresIn": 86400
  }
}
```

---

### 2.4 用户登出
**POST** `/auth/logout`

**请求头**：`Authorization: Bearer {token}`

**响应示例**：
```json
{
  "code": 200,
  "message": "登出成功"
}
```

---

### 2.5 修改密码
**PUT** `/auth/password`

**请求头**：`Authorization: Bearer {token}`

**请求参数**：
```json
{
  "oldPassword": "string",
  "newPassword": "string"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "密码修改成功"
}
```

---

### 2.6 密码重置请求
**POST** `/auth/reset-request`

**请求参数**：
```json
{
  "email": "string"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "重置链接已发送到邮箱"
}
```

---

### 2.7 密码重置确认
**POST** `/auth/reset-confirm`

**请求参数**：
```json
{
  "resetToken": "string",
  "newPassword": "string"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "密码重置成功"
}
```

---

### 2.8 获取当前用户信息
**GET** `/auth/me`

**请求头**：`Authorization: Bearer {token}`

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": "1001",
    "username": "jujiu",
    "email": "jujiu@example.com",
    "nickname": "居九",
    "role": "USER"
  }
}
```

---

## 3. 对话接口

### 3.1 发送消息
**POST** `/chat/send`

**请求头**：
```
Authorization: Bearer {token}
```

**请求参数**：
```json
{
  "sessionId": "session_123",
  "message": "你好，今天天气怎么样？"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "session_123",
    "messageId": "msg_456",
    "reply": "今天天气晴朗，温度适宜...",
    "conversationRound": 3,
    "timestamp": "2026-03-19T10:00:00Z"
  }
}
```

---

### 3.2 创建会话
**POST** `/chat/session`

**请求头**：
```
Authorization: Bearer {token}
```

**请求参数**：
```json
{
  "title": "关于天气的对话"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "session_123",
    "title": "关于天气的对话",
    "createdAt": "2026-03-19T10:00:00Z"
  }
}
```

---

### 3.3 获取会话列表
**GET** `/chat/sessions`

**请求参数**：
- page: 页码（默认1）
- size: 每页数量（默认10）

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 25,
    "page": 1,
    "size": 10,
    "sessions": [
      {
        "sessionId": "session_123",
        "title": "关于天气的对话",
        "lastMessage": "今天天气晴朗...",
        "messageCount": 5,
        "createdAt": "2026-03-19T10:00:00Z",
        "updatedAt": "2026-03-19T11:00:00Z"
      }
    ]
  }
}
```

---

### 3.4 获取会话详情
**GET** `/chat/session/{sessionId}`

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "session_123",
    "title": "关于天气的对话",
    "messages": [
      {
        "messageId": "msg_1",
        "role": "user",
        "content": "你好",
        "timestamp": "2026-03-19T10:00:00Z"
      },
      {
        "messageId": "msg_2",
        "role": "assistant",
        "content": "你好！有什么可以帮助你的吗？",
        "timestamp": "2026-03-19T10:00:01Z"
      }
    ]
  }
}
```

---

### 3.5 删除会话
**DELETE** `/chat/session/{sessionId}`

**响应示例**：
```json
{
  "code": 200,
  "message": "删除成功"
}
```

---

## 4. 工具接口

### 4.1 获取工具列表
**GET** `/tools`

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tools": [
      {
        "toolId": "weather",
        "name": "天气查询",
        "description": "查询指定城市的天气信息",
        "parameters": [
          {
            "name": "city",
            "type": "string",
            "required": true,
            "description": "城市名称"
          }
        ]
      }
    ]
  }
}
```

---

### 4.2 执行工具
**POST** `/tools/execute`

**请求参数**：
```json
{
  "toolName": "weather",
  "parameters": {
    "city": "北京"
  }
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "result": "北京今天晴，温度15-25℃",
    "executionTime": 150
  }
}
```

---

## 5. 历史记录接口

### 5.1 查询对话历史
**GET** `/history/conversations`

**请求参数**：
- startDate: 开始日期（可选）
- endDate: 结束日期（可选）
- keyword: 关键词（可选）
- page: 页码
- size: 每页数量

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "conversations": [
      {
        "sessionId": "session_123",
        "messageId": "msg_456",
        "userMessage": "今天天气怎么样？",
        "assistantReply": "今天天气晴朗...",
        "timestamp": "2026-03-19T10:00:00Z"
      }
    ]
  }
}
```

---

### 5.2 导出对话记录
**POST** `/history/export`

**请求参数**：
```json
{
  "sessionId": "session_123",
  "format": "json"
}
```

**响应**：文件下载

---

## 6. 工作流接口

### 6.1 创建工作流
**POST** `/workflow`

**请求参数**：
```json
{
  "name": "天气查询工作流",
  "definition": {
    "nodes": [
      {
        "id": "node1",
        "type": "tool",
        "config": {
          "toolName": "weather"
        }
      }
    ],
    "edges": []
  }
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "workflowId": "wf_123",
    "name": "天气查询工作流"
  }
}
```

---

### 6.2 执行工作流
**POST** `/workflow/{workflowId}/execute`

**请求参数**：
```json
{
  "input": {
    "city": "北京"
  }
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "executionId": "exec_456",
    "status": "completed",
    "result": "工作流执行成功"
  }
}
```

---

## 7. 用户管理接口（管理员）

### 7.1 获取用户列表
**GET** `/admin/users`

**权限**：ADMIN

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 50,
    "users": [
      {
        "userId": "1001",
        "username": "jujiu",
        "email": "jujiu@example.com",
        "role": "USER",
        "status": 1,
        "createdAt": "2026-03-19T10:00:00Z"
      }
    ]
  }
}
```

---

## 8. 系统接口

### 8.1 健康检查
**GET** `/health`

**响应示例**：
```json
{
  "status": "UP",
  "components": {
    "database": "UP",
    "redis": "UP",
    "deepseek": "UP"
  }
}
```

---

### 8.2 系统信息
**GET** `/info`

**响应示例**：
```json
{
  "version": "1.0.0",
  "buildTime": "2026-03-19T10:00:00Z",
  "environment": "production"
}
```

---

## 9. 错误响应格式

### 9.1 业务错误
```json
{
  "code": 1003,
  "message": "用户名或密码错误",
  "timestamp": "2026-03-19T10:00:00Z"
}
```

### 9.2 系统错误
```json
{
  "code": 500,
  "message": "系统内部错误",
  "timestamp": "2026-03-19T10:00:00Z"
}
```

---

## 10. 接口清单汇总

| 分类 | 接口数量 | 接口列表 |
|------|----------|----------|
| 认证接口 | 8 | 注册、登录、刷新、登出、密码管理等 |
| 对话接口 | 5 | 发送消息、会话管理等 |
| 工具接口 | 2 | 工具列表、工具执行 |
| 历史接口 | 2 | 查询历史、导出记录 |
| 工作流接口 | 2 | 创建工作流、执行工作流 |
| 管理接口 | 1 | 用户管理 |
| 系统接口 | 2 | 健康检查、系统信息 |
| **总计** | **22** | - |

---

**文档状态**：✅ 已完成
**接口总数**：22个
**下一步**：生成Swagger/OpenAPI文档
