# 修复：AgentOZ重复发送系统提示词问题

## 📋 问题描述

**现象**：每次用户对话时，都会在历史消息中增加一个重复的"system"角色消息记录，导致系统提示词不断累积。

**根本原因**：
- AgentOZ每次调用codex-agent时都会发送完整的SessionConfig（包含baseInstructions和developerInstructions）
- codex-agent在恢复会话时，会先从history_rollout重建历史（已包含配置），然后**无条件地再次添加当前的initial_context**（又包含一次配置）
- 导致配置重复添加，每次对话增加一次系统提示词

**关键代码位置**：
- `codex-agent/codex-rs/core/src/codex.rs:864-867` - codex-agent重复添加配置

## ✅ 极简解决方案

### 核心思路
**有历史记录就不传配置，没历史记录才传配置。**

因为：
1. ✅ 配置不支持运行时变更
2. ✅ history_rollout中已包含完整配置
3. ✅ codex-agent会从rollout中恢复配置

### 代码实现

**文件**: `agentoz-server/src/main/java/com/deepknow/agentoz/manager/AgentExecutionManager.java:177-191`

```java
// 7. 🔧 简单策略：有历史记录就不传配置
// 原因：配置不支持变更，history_rollout中已包含完整配置
boolean hasHistory = (historyRollout != null && historyRollout.length > 0);

SessionConfig sessionConfig;

if (hasHistory) {
    // 有历史记录，发送空配置（避免重复发送指令）
    log.info("⏩ 检测到历史记录，跳过发送配置: agentId={}", agentId);
    sessionConfig = SessionConfig.getDefaultInstance();
} else {
    // 首次调用，发送完整配置
    log.info("✨ 首次调用，发送完整配置: agentId={}", agentId);
    sessionConfig = ConfigProtoConverter.toSessionConfig(config);
}
```

### 修改的文件

**仅1个文件，修改11行代码**：
- `agentoz-server/src/main/java/com/deepknow/agentoz/manager/AgentExecutionManager.java`

## 🎯 工作流程

```
┌─────────────────────────────────────────┐
│ 第一轮对话（history_rollout = 空）        │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│ hasHistory = false                       │
│ → 发送完整配置（包含指令）                │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│ codex-agent 返回 updated_rollout        │
│ 包含：TurnContext(配置) + 消息历史       │
└─────────────────────────────────────────┘

第二轮对话（有 history_rollout）
              ↓
┌─────────────────────────────────────────┐
│ hasHistory = true                        │
│ → 发送空配置（getDefaultInstance）      │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│ codex-agent 从 rollout 恢复配置         │
│ 不再重复添加系统提示词 ✅                │
└─────────────────────────────────────────┘
```

## 🚀 部署步骤

### 1. 编译部署
```bash
# 编译项目
cd agentoz
mvn clean package -DskipTests

# 部署新的jar包
```

### 2. 验证修复效果

**首次调用**：
```
准备调用Codex: agentId=xxx, model=qwen-max, historySize=0 bytes
✨ 首次调用，发送完整配置: agentId=xxx
[DEBUG] 提示词配置: baseInstructions长度=1234, developerInstructions长度=5678
```

**后续调用**：
```
准备调用Codex: agentId=xxx, model=qwen-max, historySize=5120 bytes
⏩ 检测到历史记录，跳过发送配置: agentId=xxx
[DEBUG] 提示词配置: baseInstructions长度=0, developerInstructions长度=0
```

## 📊 优化效果

### Token节省
假设：
- baseInstructions: 1000 tokens
- developerInstructions: 2000 tokens
- 每天对话次数: 100次
- 有历史记录比例: 99%

**节省**：
- 每次对话节省: 3000 tokens
- 每天节省: 3000 × 100 × 0.99 = **297,000 tokens**
- 每月节省: 297,000 × 30 = **8,910,000 tokens**

### 代码简化
- 删除了200+行复杂的配置检测逻辑
- 无需计算哈希值
- 无需解析rollout
- 无需数据库变更
- 无需修改Entity

### 消息列表优化
**修复前**：
```
[系统提示词, User: 问题1, Assistant: 答案1,
 系统提示词, User: 问题2, Assistant: 答案2,
 系统提示词, User: 问题3, Assistant: 答案3, ...]
每次增加一个系统提示词 ❌
```

**修复后**：
```
[系统提示词, User: 问题1, Assistant: 答案1,
 User: 问题2, Assistant: 答案2,
 User: 问题3, Assistant: 答案3, ...]
系统提示词仅在开头出现一次 ✅
```

## ⚠️ 注意事项

### 1. 配置不支持变更
此方案的前提是：**配置在会话创建后就不再变更**。

如果未来需要支持配置变更，需要：
- 方案A：在codex-agent端添加配置合并逻辑（检测配置是否相同）
- 方案B：在AgentOZ端检测配置变更并重新发送完整配置

### 2. 空配置的含义
`SessionConfig.getDefaultInstance()` 返回的是**空配置**，所有字段都是默认值。

codex-agent收到空配置时会：
- 从history_rollout中恢复TurnContext
- 使用rollout中的配置（指令、模型、策略等）
- 不会添加新的initial_context

### 3. 何时会发送完整配置
仅以下情况发送完整配置：
- ✅ 新建会话（history_rollout为空）
- ✅ 会话被清空（history_rollout被删除）

## 🔍 故障排查

### 问题1: 后续对话仍然收到配置
**检查**：
```bash
# 查看日志
grep "检测到历史记录" logs/application.log
```

**预期**：第二次及以后的对话应该出现 "⏩ 检测到历史记录，跳过发送配置"

**原因**：
- historyRollout可能为空
- 检查codex-agent是否正确返回updated_rollout
- 检查agent.activeContext是否正确保存

### 问题2: 首次对话没有发送配置
**检查**：
```bash
# 查看日志
grep "首次调用" logs/application.log
```

**预期**：第一次对话应该出现 "✨ 首次调用，发送完整配置"

**原因**：
- historyRollout可能不是0字节
- 检查是否有初始化数据

### 问题3: codex-agent报错缺少配置
**症状**：codex-agent报错说找不到配置信息

**解决方案**：
- 确认首次调用发送了完整配置
- 确认后续调用使用了getDefaultInstance()而不是null
- 检查codex-agent是否正确从rollout恢复配置

## 💡 为什么这个方案更好

| 对比项 | 复杂方案（哈希检测） | 极简方案（有历史就不传） |
|--------|---------------------|----------------------|
| 代码量 | +200行 | +11行 |
| 数据库变更 | 需要添加字段 | ❌ 不需要 |
| Entity变更 | 需要添加字段 | ❌ 不需要 |
| 运行时开销 | 需要计算哈希、解析rollout | 仅判断数组是否为空 |
| 可维护性 | 复杂，容易出错 | 简单，一目了然 |
| 前提条件 | 无 | 配置不支持变更 |

## 📚 相关文档

- [Codex-Agent配置管理](../../codex-agent/docs/configuration.md)
- [Codex-Agent rollout格式](../../codex-agent/docs/rollout-format.md)
- [Proto协议定义](../proto/adapter.proto)

## 📝 变更历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|----------|------|
| 1.0.4 | 2025-01-17 | **极简方案**：有历史就不传配置，仅11行代码 | Claude |
| 1.0.3 | 2025-01-17 | ~~从rollout提取配置进行比较~~ (过度设计) | - |
| 1.0.2 | 2025-01-17 | ~~添加config_summary字段~~ (不需要改表) | - |
| 1.0.1 | 2025-01-11 | 添加缺失字段，修复Entity与表结构不匹配 | - |
| 1.0.0 | 2025-01-11 | 初始Schema设计 | - |
