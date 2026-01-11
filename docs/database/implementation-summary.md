# 历史记录功能实现总结

## 📋 实现内容

### 1. 创建的历史管理工具类

#### ConversationHistoryManager（会话历史管理器）
**路径**: `com.deepknow.agentoz.infra.history.ConversationHistoryManager`

**功能**:
- ✅ 追加用户消息到会话历史
- ✅ 追加 Assistant 响应到会话历史
- ✅ 追加函数调用记录到会话历史
- ✅ 追加函数返回结果到会话历史
- ✅ 立即写库

**核心方法**:
```java
// 追加用户消息
appendUserMessage(String conversationId, String userMessage)

// 追加 Assistant 响应
appendAssistantMessage(String conversationId, String assistantMessage)

// 追加函数调用
appendFunctionCall(String conversationId, String callId, String functionName, String arguments)

// 追加函数返回
appendFunctionCallOutput(String conversationId, String callId, String output)
```

#### AgentContextManager（Agent 上下文管理器）
**路径**: `com.deepknow.agentoz.infra.history.AgentContextManager`

**功能**:
- ✅ 追加交互到 Agent 的 activeContext
- ✅ 更新 Agent 的 stateDescription
- ✅ 更新交互统计（interactionCount, lastInteractionType, lastInteractionAt）
- ✅ 立即写库

**核心方法**:
```java
// Agent 被调用时
onAgentCalled(String agentId, String inputMessage)

// Agent 返回响应时
onAgentResponse(String agentId, String responseMessage)

// Agent 调用工具时
onAgentCalledTool(String agentId, String callId, String toolName, String arguments)

// 工具返回结果时
onToolReturned(String agentId, String callId, String output)
```

### 2. 修改的服务类

#### AgentExecutionServiceImpl
**修改内容**:

在 `executeTask` 方法中集成了历史记录逻辑：

```java
@Override
public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
    // ... 参数校验和 Agent 查找 ...

    // ✅ 步骤 1: 记录用户消息到会话历史（所有 Agent 共享）
    conversationHistoryManager.appendUserMessage(conversationId, userMessage);

    // ✅ 步骤 2: 记录 Agent 被调用状态
    agentContextManager.onAgentCalled(finalAgentId, userMessage);

    // ... 准备调用 Codex-Agent ...

    // ✅ 步骤 3: 调用 Codex-Agent，并在响应返回时记录历史
    codexAgentClient.runTask(
        ...,
        StreamGuard.wrapObserver(responseObserver, proto -> {
            TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);

            // ✅ 记录 Assistant 响应到会话历史
            if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
                conversationHistoryManager.appendAssistantMessage(conversationId, dto.getFinalResponse());

                // ✅ 记录 Agent 返回状态
                agentContextManager.onAgentResponse(finalAgentId, dto.getFinalResponse());
            }

            responseObserver.onNext(dto);
        }, traceInfo)
    );
}
```

### 3. 数据流示意

```
用户发起对话
    │
    ├─→ conversation.historyContext 追加用户消息 ✅
    │
    ├─→ agent.activeContext 追加用户消息 ✅
    │
    ├─→ agent.stateDescription 更新为 "输入: ..." ✅
    │
    └─→ 调用 Codex-Agent
            │
            ├─→ 返回响应
            │
            ├─→ conversation.historyContext 追加响应消息 ✅
            │
            ├─→ agent.activeContext 追加响应消息 ✅
            │
            └─→ agent.stateDescription 更新为 "... | 输出: ..." ✅
```

## 🎯 实现的功能特性

### ✅ 已实现

1. **用户消息记录**
   - 每次用户发送消息时，自动追加到 `conversation.historyContext`
   - 更新 `conversation.messageCount`、`lastMessageContent`、`lastMessageType`、`lastMessageAt`
   - 立即写库

2. **Agent 被调用记录**
   - 每次调用 Agent 时，自动追加到 `agent.activeContext`
   - 更新 `agent.stateDescription`（输入摘要）
   - 更新 `agent.interactionCount`、`lastInteractionType`、`lastInteractionAt`
   - 立即写库

3. **Agent 响应记录**
   - 每次 Agent 返回响应时，自动追加到 `conversation.historyContext` 和 `agent.activeContext`
   - 更新 `agent.stateDescription`（追加结果摘要）
   - 更新交互统计
   - 立即写库

4. **工具调用记录**
   - 提供了 `onAgentCalledTool` 和 `onToolReturned` 方法
   - 预留了函数调用和函数返回的记录能力

### ⏳ 待完善

1. **HistoryItem 序列化/反序列化**
   - 当前 `ConversationHistoryManager` 和 `AgentContextManager` 中的序列化/反序列化逻辑是 TODO
   - 需要实现 JSON 格式的 HistoryItem 序列化
   - 可以使用 Codex-Agent 返回的 `new_items_json` 格式

2. **多 Agent 协作场景**
   - 当 Agent A 调用 Agent B 时，需要同时更新两个 Agent 的历史
   - 需要在 `CallAgentTool` 中集成历史记录逻辑

3. **工具调用记录**
   - 需要在实际调用工具的地方（比如 MCP 工具调用）调用 `onAgentCalledTool`
   - 需要在工具返回时调用 `onToolReturned`

## 📝 使用示例

### 场景 1：简单对话

```java
// 用户：帮我查北京天气
executeTask(request, responseObserver);
```

**执行流程**:
1. `conversationHistoryManager.appendUserMessage(conversationId, "帮我查北京天气")`
   - conversation.historyContext = [{"message": {"role": "user", "content": [{"text": "帮我查北京天气"}]}}]
   - conversation.messageCount = 1

2. `agentContextManager.onAgentCalled(agentId, "帮我查北京天气")`
   - agent.activeContext = [{"message": {"role": "user", "content": [{"text": "帮我查北京天气"}]}}]
   - agent.stateDescription = "输入: 帮我查北京天气"

3. Codex-Agent 返回响应："北京今天晴天，温度25°C"

4. `conversationHistoryManager.appendAssistantMessage(conversationId, "北京今天晴天，温度25°C")`
   - conversation.historyContext = [..., {"message": {"role": "assistant", ...}}]
   - conversation.messageCount = 2

5. `agentContextManager.onAgentResponse(agentId, "北京今天晴天，温度25°C")`
   - agent.activeContext = [..., {"message": {"role": "assistant", ...}}]
   - agent.stateDescription = "输入: 帮我查北京天气 | 输出: 北京今天晴天，温度25°C"

### 场景 2：多 Agent 协作

```
用户 → Agent A → Agent B
```

**执行流程**:
1. 用户发起对话
   - conversation.historyContext 追加用户消息
   - agent A.activeContext 追加用户消息
   - agent A.stateDescription = "输入: ..."

2. Agent A 调用 Agent B
   - agent A.activeContext 追加 "正在调用AgentB"
   - agent A.stateDescription = "输入: ... | 调用工具: call_agent"

3. Agent B 处理并返回
   - agent B.activeContext 追加 Agent A 的调用消息
   - agent B.activeContext 追加 Agent B 的响应
   - agent B.stateDescription = "输入: ... | 输出: ..."

4. Agent A 最终返回给用户
   - conversation.historyContext 追加 Agent A 的最终响应
   - agent A.activeContext 追加最终响应
   - agent A.stateDescription = "输入: ... | 输出: ..."

## 🔧 下一步工作

1. **实现 HistoryItem 序列化/反序列化**
   - 参考 Codex-Agent 的 `new_items_json` 格式
   - 实现 JSON 和 HistoryItem 之间的转换

2. **完善工具调用记录**
   - 在 `CallAgentTool` 中集成历史记录
   - 在工具执行时记录函数调用和返回

3. **测试验证**
   - 编写单元测试验证历史记录功能
   - 测试多轮对话场景
   - 测试多 Agent 协作场景

4. **性能优化**
   - 考虑批量写库（如果性能成为瓶颈）
   - 考虑添加缓存层

## ✅ 编译状态

- ✅ 代码编译成功
- ✅ 所有类和方法都已创建
- ⏳ 需要部署测试实际运行效果
