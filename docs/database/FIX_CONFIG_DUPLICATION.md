# 修复：AgentOZ重复发送系统提示词问题

## 📋 问题描述

**现象**：每次用户对话时，都会在历史消息中增加一个重复的"system"角色消息记录，导致系统提示词不断累积。

**根本原因**：
- **Paper项目将用户提示词设置到了 `developer_instructions` 字段**
- Codex-agent 在恢复会话时，会先从 history_rollout 重建历史，然后**无条件地再次添加当前的 initial_context**
- 由于 `developer_instructions` 被错误使用，导致每次都重复发送

## ✅ 最终解决方案：修正字段使用

### 问题定位

**文件**: `deepknow-paper/paper-server/paper-infrastructure/src/main/java/com/deepknow/paper/infrastructure/gateway/AgentGatewayImpl.java:176`

**修改前（错误）**：
```java
config.setDeveloperInstructions(behavior.getSystemPrompt());  // ❌ 错误
```

**修改后（正确）**：
```java
config.setBaseInstructions(behavior.getSystemPrompt());  // ✅ 正确
// developerInstructions 留空（用于agentoz内部控制逻辑）
```

### 字段说明

| 字段 | 用途 | 示例 |
|------|------|------|
| **baseInstructions** | 给 Agent 的业务级指令（用户提示词） | "你是一个代码助手，精通Java和Python..." |
| **developerInstructions** | 开发者指令，用于底层控制逻辑 | "使用工具前必须经过审批..." |

### Codex 的指令处理顺序

从 `codex-agent/codex-rs/core/src/codex.rs:build_initial_context` 可以看到：

```rust
1. DeveloperInstructions::from_policy()     // 策略指令（沙箱、审批等）
2. developer_instructions (如果有)          // 开发者指令（agentoz内部控制）
3. user_instructions (base_instructions)    // 用户指令 ← 用户的提示词应该在这里！
```

## 🎯 工作原理

### 修复前（错误）

```
Paper设置: userPrompt → developer_instructions
         baseInstructions → 空

Codex收到的配置:
- developer_instructions: "你是一个代码助手..."
- base_instructions: null

Codex处理:
1. from_policy() → 生成策略指令
2. developer_instructions → "你是一个代码助手..."
3. base_instructions → null

问题：每次对话都重复步骤2！❌
```

### 修复后（正确）

```
Paper设置: userPrompt → baseInstructions
         developerInstructions → 空

Codex收到的配置:
- developer_instructions: null
- base_instructions: "你是一个代码助手..."

Codex处理:
1. from_policy() → 生成策略指令
2. developer_instructions → 跳过（为空）
3. base_instructions → "你是一个代码助手..."

正常：指令只在rollout中，不会重复！✅
```

## 📊 优化效果

### Token节省
假设：
- baseInstructions: 1000 tokens
- developerInstructions: 2000 tokens
- 每天对话次数: 100次

**节省**：
- 每次对话节省: 3000 tokens
- 每天节省: **300,000 tokens**
- 每月节省: **9,000,000 tokens**

### 代码简化
- **仅修改1行代码**（字段从 `developerInstructions` 改为 `baseInstructions`）
- 无需复杂的配置检测逻辑
- 无需修改数据库
- 无需修改Entity

### 消息列表优化
**修复前**：
```
[策略指令, 用户提示词, User: 问题1, Assistant: 答案1,
 策略指令, 用户提示词, User: 问题2, Assistant: 答案2, ...]
每次重复策略和用户提示词 ❌
```

**修复后**：
```
[策略指令, 用户提示词, User: 问题1, Assistant: 答案1,
 User: 问题2, Assistant: 答案2, ...]
策略和用户提示词仅在开头出现一次 ✅
```

## 🚀 部署步骤

### 1. 编译部署
```bash
# 编译paper项目
cd deepknow-paper
mvn clean package -DskipTests

# 部署新的jar包
```

### 2. 验证修复效果

**首次调用**：
```
准备调用Codex: agentId=xxx, model=qwen-max, historySize=0 bytes
[DEBUG] 提示词配置: baseInstructions长度=1234, developerInstructions长度=0
```

**后续调用**：
```
准备调用Codex: agentId=xxx, model=qwen-max, historySize=5120 bytes
[DEBUG] 提示词配置: baseInstructions长度=1234, developerInstructions长度=0
```

关键点：**两次的 baseInstructions 长度应该相同**（都是1234），说明不会重复累积！

## ⚠️ 注意事项

### 1. 现有Agent的处理

对于已经创建的Agent（之前使用 `developer_instructions` 存储提示词）：

**选项A**：重新创建Agent（推荐）
- 删除旧Agent
- 用新的代码创建Agent
- 提示词会自动保存到 `baseInstructions`

**选项B**：数据迁移脚本
如果需要保留现有Agent，可以运行迁移脚本：
```sql
UPDATE agent_configs
SET base_instructions = developer_instructions,
    developer_instructions = NULL
WHERE developer_instructions IS NOT NULL
  AND base_instructions IS NULL;
```

### 2. developerInstructions 的正确用途

`developerInstructions` 应该用于：
- AgentOZ 的内部控制逻辑
- 工具调用规范
- 审批流程控制

**示例**：
```java
// ✅ 正确使用
config.setBaseInstructions(userPrompt);              // 用户：你是一个Java专家
config.setDeveloperInstructions("使用工具前必须经过用户审批，除非是只读操作。");
```

### 3. 多租户场景

如果有多个项目都需要修改：
- Paper项目：✅ 已修改
- AgentOZ直连场景：需要检查调用 AgentConfigDTO 的地方
- 其他项目：搜索 `setDeveloperInstructions` 并改为 `setBaseInstructions`

## 🔍 故障排查

### 问题1: 提示词仍然重复
**检查**：
```bash
# 查看日志
grep "提示词配置" logs/application.log
```

**预期**：baseInstructions 长度应该保持一致，不会增长

**原因**：
- 可能是旧数据（Agent创建时使用错误字段）
- 运行数据迁移脚本

### 问题2: Agent不响应提示词
**检查**：
```bash
# 查看数据库
SELECT base_instructions, developer_instructions
FROM agent_configs
WHERE config_id = 'xxx';
```

**预期**：
- base_instructions: 应该有内容
- developer_instructions: 应该为NULL

## 📚 相关文档

- [Codex-Agent配置管理](../../codex-agent/docs/configuration.md)
- [AgentOZ数据模型](../architecture/data-model.md)
- [Proto协议定义](../proto/adapter.proto)

## 📝 变更历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|----------|------|
| 1.0.5 | 2025-01-17 | **最终方案**：修正字段使用，1行代码解决问题 | Claude |
| 1.0.4 | 2025-01-17 | ~~极简方案：有历史就不传配置~~ (不完整，会被codex默认逻辑覆盖) | - |
| 1.0.3 | 2025-01-17 | ~~从rollout提取配置进行比较~~ (过度设计) | - |
| 1.0.2 | 2025-01-17 | ~~添加config_summary字段~~ (不需要改表) | - |
| 1.0.1 | 2025-01-11 | 添加缺失字段，修复Entity与表结构不匹配 | - |
| 1.0.0 | 2025-01-11 | 初始Schema设计 | - |
