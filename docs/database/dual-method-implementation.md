# 双方法实现总结

## 📋 实现内容

本次实现了两个不同的 Agent 调用方法，分别适用于不同的使用场景：

### 1. `executeTask` - 用户发起对话（广播模式）

**方法签名**:
```java
void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver)
```

**特点**:
- ✅ 自动路由到主智能体（isPrimary=true）
- ✅ 追加到会话历史（`conversation.historyContext`）
- ✅ 广播到会话中的**所有 Agent**（调用所有 Agent 的 `onAgentCalled`）
- ✅ 记录主智能体的响应（`conversation.historyContext` 和主智能体 `activeContext`）

**使用场景**:
- 用户发起的对话
- 需要所有 Agent 都感知到用户输入
- 对话历史需要完整记录

**执行流程**:
```
用户发起对话
    │
    ├─→ conversation.historyContext 追加用户消息 ✅
    │
    ├─→ 查询会话中的所有 Agent ✅
    │
    ├─→ 对每个 Agent 调用 onAgentCalled (广播) ✅
    │
    ├─→ 找到主智能体 ✅
    │
    ├─→ 调用 Codex-Agent 计算主智能体的响应 ✅
    │
    ├─→ conversation.historyContext 追加主智能体响应 ✅
    │
    └─→ 主智能体 activeContext 追加响应 ✅
```

**代码实现**: `AgentExecutionServiceImpl.java:65-153`

---

### 2. `executeTaskToSingleAgent` - Agent 间调用（单点模式）

**方法签名**:
```java
void executeTaskToSingleAgent(String agentId, String conversationId, String message,
                              StreamObserver<TaskResponse> responseObserver)
```

**特点**:
- ✅ 直接使用指定的 Agent（不自动路由）
- ❌ **不追加到会话历史**（因为是 Agent 间调用）
- ✅ **只追加到目标 Agent 的 activeContext**
- ❌ 不影响其他 Agent 的上下文
- ✅ 记录目标 Agent 的响应（仅目标 Agent `activeContext`）

**使用场景**:
- Agent 间相互调用（Agent A → Agent B）
- 不需要其他 Agent 感知到这次调用
- 对话历史不需要记录 Agent 间调用

**执行流程**:
```
Agent A 调用 Agent B
    │
    ├─→ 不追加到 conversation.historyContext ❌
    │
    ├─→ 仅对 Agent B 调用 onAgentCalled ✅
    │
    ├─→ 调用 Codex-Agent 计算 Agent B 的响应 ✅
    │
    └─→ Agent B activeContext 追加响应 ✅
```

**代码实现**: `AgentExecutionServiceImpl.java:221-305`

---

## 🔑 核心差异对比

| 维度 | executeTask | executeTaskToSingleAgent |
|------|-------------|--------------------------|
| **路由方式** | 自动路由到主智能体 | 必须指定 AgentId |
| **会话历史** | ✅ 追加到 `conversation.historyContext` | ❌ 不追加 |
| **Agent 广播** | ✅ 广播到所有 Agent | ❌ 仅目标 Agent |
| **响应记录** | ✅ 会话历史 + 主智能体上下文 | ✅ 仅目标 Agent 上下文 |
| **典型场景** | 用户发起对话 | Agent 间调用 |
| **其他 Agent** | 会感知到用户输入 | 不会感知 |

---

## 📝 代码关键点

### 1. 广播逻辑（executeTask 专用）

```java
// ✅ 步骤 2: 广播用户消息到会话中的所有 Agent
broadcastUserMessageToAllAgents(conversationId, userMessage);
```

**实现**: `AgentExecutionServiceImpl.java:194-219`
```java
private void broadcastUserMessageToAllAgents(String conversationId, String userMessage) {
    // 查询会话中的所有 Agent
    List<AgentEntity> allAgents = agentRepository.selectList(
            new LambdaQueryWrapper<AgentEntity>()
                    .eq(AgentEntity::getConversationId, conversationId)
    );

    // 对每个 Agent 记录用户消息
    for (AgentEntity agent : allAgents) {
        agentContextManager.onAgentCalled(agent.getAgentId(), userMessage);
    }
}
```

### 2. 单点调用逻辑（executeTaskToSingleAgent 专用）

```java
// ⚠️ 注意：不追加到会话历史（因为是 Agent 间调用）

// ✅ 步骤 1: 仅记录目标 Agent 被调用状态
agentContextManager.onAgentCalled(agentId, message);
```

**关键区别**:
- 不调用 `conversationHistoryManager.appendUserMessage()`
- 不调用 `broadcastUserMessageToAllAgents()`
- 只调用 `agentContextManager.onAgentCalled(agentId, message)`

### 3. 响应处理差异

**executeTask** (广播模式):
```java
// ✅ 记录 Assistant 响应到会话历史
if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
    conversationHistoryManager.appendAssistantMessage(conversationId, dto.getFinalResponse());

    // ✅ 记录主智能体返回状态
    agentContextManager.onAgentResponse(finalAgentId, dto.getFinalResponse());
}
```

**executeTaskToSingleAgent** (单点模式):
```java
// ⚠️ 注意：不追加到会话历史（因为是 Agent 间调用）

// ✅ 仅记录目标 Agent 返回状态
if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
    agentContextManager.onAgentResponse(agentId, dto.getFinalResponse());
}
```

---

## 🎯 使用示例

### 场景 1: 用户发起对话（使用 executeTask）

```java
// 用户：帮我查北京天气
ExecuteTaskRequest request = new ExecuteTaskRequest();
request.setConversationId("conv-123");
request.setAgentId(null);  // 不传，自动路由到主智能体
request.setMessage("帮我查北京天气");

agentExecutionService.executeTask(request, responseObserver);
```

**执行结果**:
- `conversation.historyContext`: 追加用户消息 + 主智能体响应
- `所有 Agent.activeContext`: 都追加了用户消息
- `主智能体.activeContext`: 追加了用户消息 + 响应

### 场景 2: Agent 间调用（使用 executeTaskToSingleAgent）

```java
// Agent A 调用 Agent B
agentExecutionService.executeTaskToSingleAgent(
    "agent-b-id",       // 目标 Agent ID
    "conv-123",         // 会话 ID
    "帮我查北京天气",   // 消息
    responseObserver
);
```

**执行结果**:
- `conversation.historyContext`: 不变（没有追加）
- `Agent B.activeContext`: 追加了消息 + 响应
- `其他 Agent.activeContext`: 不变

---

## ✅ 编译状态

- ✅ 代码编译成功（`mvn clean compile`）
- ✅ 所有模块编译通过
- ⏳ 待部署测试实际运行效果

---

## 🔧 下一步工作

1. **部署测试**
   - 部署到测试环境
   - 验证 executeTask 的广播逻辑
   - 验证 executeTaskToSingleAgent 的单点逻辑

2. **集成 CallAgentTool**
   - 在 CallAgentTool 中调用 executeTaskToSingleAgent
   - 实现 Agent A → Agent B 的调用链

3. **完善历史记录**
   - 实现 HistoryItem 序列化/反序列化
   - 测试多轮对话场景
   - 测试多 Agent 协作场景

4. **性能优化**
   - 考虑批量写库（如果性能成为瓶颈）
   - 考虑添加缓存层

---

## 📚 相关文档

- [数据库设计文档](./storage-design.md)
- [历史管理实现总结](./implementation-summary.md)
- [API 接口定义](/Users/zhangzihao/通用智能体/重构项目/agentoz/agentoz-api/src/main/java/com/deepknow/agentoz/api/service/AgentExecutionService.java)
- [服务实现类](/Users/zhangzihao/通用智能体/重构项目/agentoz/agentoz-server/src/main/java/com/deepknow/agentoz/provider/AgentExecutionServiceImpl.java)
