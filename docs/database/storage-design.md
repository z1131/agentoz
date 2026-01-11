# AgentOZ 数据存储设计文档

## 📋 目录
- [1. 设计概述](#1-设计概述)
- [2. 数据库表结构](#2-数据库表结构)
- [3. 存储策略](#3-存储策略)
- [4. 更新策略](#4-更新策略)
- [5. 数据格式](#5-数据格式)
- [6. 使用示例](#6-使用示例)

---

## 1. 设计概述

### 1.1 核心设计原则

**分层存储，职责清晰：**
- **Conversation（会话层）**：存储完整的用户-Agent 交互历史
- **Agent（智能体层）**：存储单个 Agent 的交互上下文和状态
- **AgentConfig（配置层）**：存储 Agent 的能力配置

### 1.2 设计目标

✅ **完整性**：不丢失任何交互记录
✅ **可追溯**：能够重现整个对话过程
✅ **高性能**：每次交互立即写库，支持高并发
✅ **易扩展**：JSON 格式支持未来结构变化

---

## 2. 数据库表结构

### 2.1 conversations（会话表）

存储会话级别的完整历史。

| 字段 | 类型 | 说明 | 更新策略 |
|------|------|------|----------|
| `conversation_id` | VARCHAR(64) | 会话唯一标识 | 创建时生成 |
| `user_id` | VARCHAR(64) | 用户ID | 创建时设置 |
| `primary_agent_id` | VARCHAR(64) | 主智能体ID | 创建时设置 |
| `history_context` | JSON | 完整对话历史 | **每次有新消息时追加** |
| `message_count` | INT | 消息总数 | 每次追加时+1 |
| `last_message_content` | TEXT | 最后一条消息内容 | 每次追加时更新 |
| `last_message_type` | VARCHAR(32) | 最后一条消息类型 | 每次追加时更新 |
| `last_message_at` | DATETIME | 最后一条消息时间 | 每次追加时更新 |
| `status` | VARCHAR(32) | 会话状态 | 按需更新 |
| `created_at` | DATETIME | 创建时间 | 自动 |
| `updated_at` | DATETIME | 更新时间 | 自动 |

**核心字段 `history_context` 存储内容：**
- ✅ 所有用户输入消息 (MessageItem with role=user)
- ✅ 所有 Agent 响应消息 (MessageItem with role=assistant)
- ✅ 所有函数调用记录 (FunctionCallItem)
- ✅ 所有函数返回结果 (FunctionCallOutputItem)

### 2.2 agents（智能体表）

存储 Agent 实例及其交互历史。

| 字段 | 类型 | 说明 | 更新策略 |
|------|------|------|----------|
| `agent_id` | VARCHAR(64) | Agent唯一标识 | 创建时生成 |
| `conversation_id` | VARCHAR(64) | 所属会话ID | 创建时设置 |
| `config_id` | VARCHAR(64) | 配置ID | 创建时设置 |
| `is_primary` | BOOLEAN | 是否主智能体 | 创建时设置 |
| `active_context` | JSON | Agent交互历史 | **被调用和返回时都追加** |
| `context_format` | VARCHAR(32) | 上下文格式版本 | 创建时设置 |
| `state_description` | TEXT | Agent状态描述 | **被调用和返回时都更新** |
| `interaction_count` | INT | 交互次数 | 每次交互时+1 |
| `last_interaction_type` | VARCHAR(32) | 最后交互类型 | 每次交互时更新 |
| `last_interaction_at` | DATETIME | 最后交互时间 | 每次交互时更新 |
| `state` | VARCHAR(32) | Agent状态 | 按需更新 |
| `created_at` | DATETIME | 创建时间 | 自动 |
| `updated_at` | DATETIME | 更新时间 | 自动 |

**核心字段 `active_context` 存储内容：**
- ✅ 用户直接发送给该 Agent 的消息
- ✅ 该 Agent 的所有响应
- ✅ 其他 Agent 调用该 Agent 的消息
- ✅ 该 Agent 调用工具的记录
- ✅ 工具返回的结果

**核心字段 `state_description` 存储内容：**
- Agent 被调用时：记录输入摘要
- Agent 返回时：追加执行结果摘要

### 2.3 agent_configs（智能体配置表）

存储 Agent 的能力配置（略，详见 schema.sql）。

---

## 3. 存储策略

### 3.1 Conversation 历史范围

**策略：存储所有历史（不删除）**

```
用户发起对话 → conversation.historyContext = [用户消息]
Agent A 响应  → conversation.historyContext = [用户消息, Agent A响应]
Agent B 被调用 → conversation.historyContext = [用户消息, Agent A响应, Agent B调用]
Agent B 响应  → conversation.historyContext = [用户消息, Agent A响应, Agent B调用, Agent B响应]
Agent A 最终响应 → conversation.historyContext = [..., Agent A最终响应]
```

### 3.2 Agent 上下文范围

**策略：存储与该 Agent 相关的所有交互**

```
场景：用户 → Agent A → Agent B → Agent C

agent A.activeContext = [
  用户消息,
  Agent A响应(正在调用B),
  Agent A最终响应
]

agent B.activeContext = [
  Agent A调用B的消息,
  Agent B响应(正在调用C),
  Agent B最终响应
]

agent C.activeContext = [
  Agent B调用C的消息,
  Agent C响应
]
```

### 3.3 写库时机

**策略：每次交互都立即写库**

| 时机 | 写库内容 |
|------|----------|
| 用户发送消息 | 更新 conversation.historyContext |
| Agent 被调用 | 更新 agent.activeContext + agent.stateDescription |
| Agent 返回响应 | 更新 conversation.historyContext + agent.activeContext + agent.stateDescription |
| Agent 调用工具 | 更新 agent.activeContext（追加 function_call） |
| 工具返回结果 | 更新 agent.activeContext（追加 function_call_output） |

---

## 4. 更新策略

### 4.1 Conversation 更新策略

```java
// 伪代码示例
void appendToConversation(String conversationId, HistoryItem newItem) {
    // 1. 读取现有历史
    ConversationEntity conversation = conversationRepository.selectById(conversationId);
    List<HistoryItem> history = parseJson(conversation.getHistoryContext());

    // 2. 追加新项
    history.add(newItem);

    // 3. 更新辅助字段
    conversation.setHistoryContext(toJson(history));
    conversation.setMessageCount(history.size());
    conversation.setLastMessageContent(extractTextContent(newItem));
    conversation.setLastMessageType(determineItemType(newItem));
    conversation.setLastMessageAt(LocalDateTime.now());

    // 4. 立即写库
    conversationRepository.updateById(conversation);
}
```

### 4.2 Agent 更新策略

#### 场景 1：Agent 被调用时

```java
// 伪代码示例
void onAgentCalled(String agentId, String inputMessage) {
    AgentEntity agent = agentRepository.selectById(agentId);

    // 1. 追加输入消息到 activeContext
    appendToAgentContext(agent, toMessageItem("user", inputMessage));

    // 2. 更新 stateDescription（输入摘要）
    String summary = generateInputSummary(inputMessage);
    agent.setStateDescription("输入: " + summary);

    // 3. 更新交互统计
    agent.setInteractionCount(agent.getInteractionCount() + 1);
    agent.setLastInteractionType("input");
    agent.setLastInteractionAt(LocalDateTime.now());

    // 4. 立即写库
    agentRepository.updateById(agent);
}
```

#### 场景 2：Agent 返回时

```java
// 伪代码示例
void onAgentResponse(String agentId, String responseMessage) {
    AgentEntity agent = agentRepository.selectById(agentId);

    // 1. 追加响应消息到 activeContext
    appendToAgentContext(agent, toMessageItem("assistant", responseMessage));

    // 2. 更新 stateDescription（追加结果摘要）
    String currentDesc = agent.getStateDescription();
    String resultSummary = generateResultSummary(responseMessage);
    agent.setStateDescription(currentDesc + " | 输出: " + resultSummary);

    // 3. 更新交互统计
    agent.setInteractionCount(agent.getInteractionCount() + 1);
    agent.setLastInteractionType("output");
    agent.setLastInteractionAt(LocalDateTime.now());

    // 4. 立即写库
    agentRepository.updateById(agent);
}
```

---

## 5. 数据格式

### 5.1 HistoryItem JSON 格式

#### MessageItem（普通消息）

```json
{
  "message": {
    "role": "user",  // 或 "assistant", "system"
    "content": [
      {
        "text": "帮我查一下北京的天气"
      }
    ]
  }
}
```

#### FunctionCallItem（函数调用）

```json
{
  "function_call": {
    "call_id": "call_abc123",
    "name": "get_weather",
    "arguments": "{\"city\": \"北京\", \"unit\": \"celsius\"}"
  }
}
```

#### FunctionCallOutputItem（函数返回）

```json
{
  "function_call_output": {
    "call_id": "call_abc123",
    "output": "{\"success\": true, \"content\": \"北京今天晴天，温度25°C\"}"
  }
}
```

### 5.2 StateDescription 格式

```
# Agent 被调用时
"输入: 帮我查北京天气"

# Agent 调用工具时
"输入: 帮我查北京天气 | 输出: 正在调用天气服务..."

# Agent 最终返回时
"输入: 帮我查北京天气 | 输出: 北京今天晴天，温度25°C"
```

---

## 6. 使用示例

### 6.1 完整对话流程示例

```
1. 用户发起对话："帮我查北京天气"

   conversation.historyContext = [
     {"message": {"role": "user", "content": [{"text": "帮我查北京天气"}]}}
   ]
   conversation.messageCount = 1

   agent A.activeContext = [
     {"message": {"role": "user", "content": [{"text": "帮我查北京天气"}]}}
   ]
   agent A.stateDescription = "输入: 帮我查北京天气"

2. Agent A 决定调用天气工具

   agent A.activeContext = [
     {"message": {"role": "user", "content": [{"text": "帮我查北京天气"}]}},
     {"message": {"role": "assistant", "content": [{"text": "好的，我来查询"}]}},
     {"function_call": {"call_id": "call_123", "name": "get_weather", "arguments": "{...}"}}
   ]
   agent A.stateDescription = "输入: 帮我查北京天气 | 输出: 正在调用天气服务"

3. 天气工具返回结果

   agent A.activeContext = [
     ...,
     {"function_call_output": {"call_id": "call_123", "output": "{...}"}}
   ]

4. Agent A 最终返回给用户

   conversation.historyContext = [
     {"message": {"role": "user", "content": [{"text": "帮我查北京天气"}]}},
     {"message": {"role": "assistant", "content": [{"text": "好的，我来查询"}]}},
     {"function_call": {"call_id": "call_123", "name": "get_weather", "arguments": "{...}"}},
     {"function_call_output": {"call_id": "call_123", "output": "{...}"}},
     {"message": {"role": "assistant", "content": [{"text": "北京今天晴天，温度25°C"}]}}
   ]
   conversation.messageCount = 5

   agent A.activeContext = [..., {"message": {"role": "assistant", "content": [{"text": "北京今天晴天，温度25°C"}]}}]
   agent A.stateDescription = "输入: 帮我查北京天气 | 输出: 北京今天晴天，温度25°C"
```

### 6.2 多 Agent 协作示例

```
场景：用户 → Agent A → Agent B → Agent C

1. 用户："帮我制定一个旅游计划"
   → conversation.historyContext: [用户消息]
   → agent A.activeContext: [用户消息]
   → agent A.stateDescription: "输入: 帮我制定旅游计划"

2. Agent A 调用 Agent B（查询天气）
   → agent A.activeContext: [用户消息, Agent A响应, 调用Agent B]
   → agent B.activeContext: [Agent A的调用请求]
   → agent B.stateDescription: "输入: 查询北京天气"

3. Agent B 返回天气信息
   → agent B.activeContext: [调用请求, 天气响应]
   → agent B.stateDescription: "输入: 查询北京天气 | 输出: 北京晴天25°C"

4. Agent A 调用 Agent C（查询景点）
   → agent A.activeContext: [..., 调用Agent C]
   → agent C.activeContext: [Agent A的调用请求]
   → agent C.stateDescription: "输入: 推荐北京景点"

5. Agent C 返回景点信息
   → agent C.activeContext: [调用请求, 景点响应]
   → agent C.stateDescription: "输入: 推荐北京景点 | 输出: 推荐故宫、长城..."

6. Agent A 最终返回完整计划
   → conversation.historyContext: [所有消息]
   → agent A.activeContext: [所有相关交互]
   → agent A.stateDescription: "输入: 帮我制定旅游计划 | 输出: 已为您制定3天行程..."
```

---

## 7. 实现检查清单

### 7.1 数据库

- [x] 创建 conversations 表（包含 historyContext 字段）
- [x] 创建 agents 表（包含 activeContext 和 stateDescription 字段）
- [x] 创建 agent_configs 表
- [ ] 添加必要的索引
- [ ] 测试插入和查询性能

### 7.2 代码实现

- [ ] 实现 ConversationHistoryManager（管理会话历史）
- [ ] 实现 AgentContextManager（管理 Agent 上下文）
- [ ] 实现 StateDescriptionGenerator（生成状态摘要）
- [ ] 在 AgentExecutionService 中集成历史记录逻辑
- [ ] 添加单元测试

### 7.3 优化建议

- [ ] 考虑使用数据库批量插入（高性能场景）
- [ ] 考虑添加缓存层（Redis）减少数据库压力
- [ ] 考虑历史归档策略（超过一定时间的历史移到归档表）
- [ ] 考虑添加历史压缩（长对话场景）

---

## 8. 总结

这个设计方案的**核心优势**：

✅ **完整性**：所有交互都被记录，不丢失任何信息
✅ **分层清晰**：Conversation、Agent、Config 三层职责明确
✅ **实时更新**：每次交互立即写库，保证数据一致性
✅ **灵活扩展**：JSON 格式支持未来结构变化
✅ **易于调试**：stateDescription 提供快速了解 Agent 状态的能力

**下一步行动**：
1. 执行 schema.sql 创建数据库表
2. 实现历史管理的工具类
3. 在 AgentExecutionService 中集成历史记录逻辑
4. 编写单元测试验证功能
