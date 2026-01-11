# AgentOZ MCP Server 实现说明

## 概述

本项目基于 **MCP Java SDK 0.12.1** 实现了标准的 Streamable-HTTP MCP Server，支持 Agent 间相互调用和协作。

## 架构设计

### 核心组件

```
com.deepknow.agentoz.mcp/
├── server/
│   ├── AgentOzMcpServer.java           # MCP Server 核心类
│   └── McpAgentController.java         # Spring HTTP Controller
├── tool/
│   └── CallAgentTool.java              # call_agent 工具实现
└── config/
    └── McpServerProperties.java        # 配置属性
```

### 技术栈

- **MCP SDK**: `io.modelcontextprotocol.sdk:mcp` (v0.12.1)
- **传输层**: `io.modelcontextprotocol.sdk:mcp-spring-webmvc`
- **协议**: JSON-RPC 2.0 over Streamable-HTTP
- **流式传输**: Server-Sent Events (SSE)

## 已实现功能

### ✅ 核心协议支持

- [x] MCP 2025-03-26 协议版本
- [x] JSON-RPC 2.0 消息格式
- [x] Streamable-HTTP 传输
- [x] SSE 流式响应
- [x] 工具发现与注册
- [x] 工具调用执行

### ✅ 工具能力

#### call_agent

允许 Agent 调用另一个 Agent 执行任务。

**参数**:
- `targetAgentId` (string, required): 目标 Agent 的 ID
- `task` (string, required): 要执行的任务描述
- `context` (string, optional): 上下文信息（JSON 格式）
- `conversationId` (string, optional): 会话 ID

**示例**:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "call_agent",
    "arguments": {
      "targetAgentId": "agent-123",
      "task": "帮我分析一下这段代码的性能问题",
      "context": "{\"userId\": \"user-456\"}",
      "conversationId": "conv-789"
    }
  }
}
```

## API 端点

### 1. HTTP 消息端点

```
POST /mcp/agent/message
Content-Type: application/json
```

处理 JSON-RPC 请求消息。

### 2. SSE 连接端点

```
GET /mcp/agent/sse
Accept: text/event-stream
```

建立服务器发送事件连接，用于流式推送响应。

### 3. 健康检查

```
GET /mcp/agent/health
```

返回 MCP Server 健康状态。

## 配置说明

在 `application.yml` 中配置 MCP Server:

```yaml
mcp:
  server:
    enabled: true                    # 是否启用 MCP Server
    server-name: agentoz            # 服务器名称
    server-version: 1.0.0           # 服务器版本
    http-endpoint: /mcp/agent/message  # HTTP 端点
    sse-endpoint: /mcp/agent/sse       # SSE 端点
```

## 使用示例

### 1. 启动服务

```bash
cd agentoz/agentoz-server
mvn clean install
mvn spring-boot:run
```

服务将在 `http://localhost:8003` 启动。

### 2. 测试健康检查

```bash
curl http://localhost:8003/mcp/agent/health
```

响应:
```json
{
  "status": "ok",
  "server": "agentoz-mcp",
  "version": "1.0.0"
}
```

### 3. 调用 call_agent 工具

```bash
curl -X POST http://localhost:8003/mcp/agent/message \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "call_agent",
      "arguments": {
        "targetAgentId": "agent-123",
        "task": "分析这段代码"
      }
    }
  }'
```

### 4. 建立 SSE 连接

```bash
curl -N http://localhost:8003/mcp/agent/sse \
  -H "Accept: text/event-stream"
```

## 与现有实现的对比

| 特性 | 原有实现 (McpSystemController) | 新实现 (MCP SDK) |
|------|--------------------------------|------------------|
| **协议版本** | MCP 2024-11-05 | MCP 2025-03-26 ✅ |
| **依赖** | 手动实现 JSON-RPC | 官方 SDK ✅ |
| **传输层** | 自定义 SSE/HTTP | 标准 Streamable-HTTP ✅ |
| **工具注册** | 手动 Map 管理 | `ToolSpecification` ✅ |
| **异步处理** | `CompletableFuture` | Reactor `Mono` ✅ |
| **错误处理** | 手动 try-catch | SDK 统一格式 ✅ |
| **能力协商** | 手动实现 | `ServerFeatures` ✅ |

## 扩展指南

### 添加新工具

1. 创建工具类（参考 `CallAgentTool.java`）:

```java
@Component
public class MyNewTool {

    public ToolSpecification getToolSpecification() {
        return ToolSpecification.builder()
                .name("my_tool")
                .description("工具描述")
                .addParameter(...)
                .build();
    }

    public Mono<ToolExecutionResult> execute(ToolExecutionRequest request) {
        // 实现工具逻辑
        return Mono.just(ToolExecutionResult.builder()
                .content("执行结果")
                .isError(false)
                .build());
    }
}
```

2. 在 `AgentOzMcpServer.java` 中注册:

```java
@Autowired
private MyNewTool myNewTool;

private ServerFeatures buildServerFeatures() {
    Tools tools = Tools.builder()
            .tool(callAgentTool.getToolSpecification())
            .toolCallHandler(callAgentTool::execute)
            .tool(myNewTool.getToolSpecification())
            .toolCallHandler(myNewTool::execute)
            .build();
    // ...
}
```

## 故障排查

### 1. 依赖冲突

如果遇到依赖冲突，检查 `pom.xml` 中的版本:

```xml
<mcp-sdk.version>0.12.1</mcp-sdk.version>
```

### 2. 端口冲突

修改 `application.yml` 中的端口:

```yaml
server:
  port: 8003
```

### 3. MCP Server 未启动

检查日志中的初始化信息:

```
>>> 初始化 AgentOZ MCP Server: agentoz v1.0.0
✅ MCP Server 初始化成功
```

## 下一步计划

- [ ] 实现 Resources API
- [ ] 实现 Prompts API
- [ ] 完善 SSE 流式响应
- [ ] 添加更多内置工具
- [ ] 支持第三方 MCP Server 连接
- [ ] 工具调用监控与审计

## 参考资料

- [MCP 官方文档](https://modelcontextprotocol.io)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [Streamable-HTTP 规范](https://modelcontextprotocol.io/specification/2025-03-26/basic/transports)

---

**作者**: AgentOZ Team
**版本**: 1.0.0
**更新时间**: 2025-01-11
