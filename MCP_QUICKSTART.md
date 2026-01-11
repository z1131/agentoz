# AgentOZ MCP Server 快速启动指南

## 🚀 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- Spring Boot 3.2.1

### 1. 编译项目

```bash
cd /Users/zhangzihao/通用智能体/重构项目/agentoz
mvn clean install -DskipTests
```

### 2. 启动服务

```bash
cd agentoz-server
mvn spring-boot:run
```

服务将在 `http://localhost:8003` 启动。

### 3. 验证服务

```bash
curl http://localhost:8003/mcp/agent/health
```

期望响应:
```json
{
  "status": "ok",
  "server": "agentoz-mcp",
  "version": "1.0.0"
}
```

## 📡 API 端点

### HTTP 端点
```
POST /mcp/agent/message
```
处理 JSON-RPC 消息

### SSE 端点
```
GET /mcp/agent/sse
```
建立服务器发送事件连接

### 健康检查
```
GET /mcp/agent/health
```

## 🔧 可用工具

### call_agent

调用另一个 Agent 执行任务。

**参数**:
- `targetAgentId` (string, 必需): 目标 Agent ID
- `task` (string, 必需): 任务描述
- `context` (string, 可选): 上下文信息 (JSON)
- `conversationId` (string, 可选): 会话 ID

## 📝 使用示例

### 列出可用工具

```bash
curl -X POST http://localhost:8003/mcp/agent/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }' | jq
```

### 调用 call_agent

```bash
curl -X POST http://localhost:8003/mcp/agent/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "call_agent",
      "arguments": {
        "targetAgentId": "agent-123",
        "task": "分析这段代码的性能问题"
      }
    }
  }' | jq
```

### 建立 SSE 连接

```bash
curl -N http://localhost:8003/mcp/agent/sse \
  -H "Accept: text/event-stream"
```

## ⚙️ 配置

编辑 `application.yml`:

```yaml
mcp:
  server:
    enabled: true
    server-name: agentoz
    server-version: 1.0.0
    http-endpoint: /mcp/agent/message
    sse-endpoint: /mcp/agent/sse
```

## 🧪 运行测试脚本

```bash
chmod +x MCP_TEST_EXAMPLES.sh
./MCP_TEST_EXAMPLES.sh
```

## 📚 更多文档

- `MCP_IMPLEMENTATION.md` - 完整实现文档
- `MCP_INTEGRATION_SUMMARY.md` - 实现总结

## 🆘 故障排查

### 依赖下载失败
```bash
mvn dependency:purge-local-repository
mvn clean install
```

### 端口被占用
修改 `application.yml` 中的端口:
```yaml
server:
  port: 8004
```

### MCP Server 未启动
查看日志:
```
>>> 初始化 AgentOZ MCP Server: agentoz v1.0.0
✅ MCP Server 初始化成功
```

## 🔗 相关资源

- [MCP 官方文档](https://modelcontextprotocol.io)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)

---

**AgentOZ Team** © 2025
