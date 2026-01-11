# ⚠️ 生产环境数据库修复 - 最终完整版

## 问题总结

生产环境报了两个错误：

### 错误 1: agents 表
```
Unknown column 'agent_name' in 'field list'
```

### 错误 2: agent_configs 表（当前问题）
```
Unknown column 'provider' in 'field list'
```

**根本原因**: 数据库表结构与 Entity 类字段定义不匹配。

---

## 🚀 快速修复方案

### 方案 1: 一键执行（推荐）

**直接执行完整迁移脚本**：
```bash
mysql -u你的用户名 -p数据庖 < docs/database/migration_complete.sql
```

这个脚本会自动修复 **agents** 和 **agent_configs** 两个表。

---

### 方案 2: 手动执行（紧急）

如果方案 1 有问题，登录 MySQL 手动执行：

```bash
mysql -u你的用户名 -p
```

```sql
use 数据库名;

-- ========================================
-- 1. 修复 agents 表
-- ========================================
ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN description TEXT;
ALTER TABLE agents ADD COLUMN priority INT DEFAULT 5;
ALTER TABLE agents ADD COLUMN state VARCHAR(32) DEFAULT 'ACTIVE';
ALTER TABLE agents ADD COLUMN full_history JSON;
ALTER TABLE agents ADD COLUMN last_used_at DATETIME;
ALTER TABLE agents ADD COLUMN created_by VARCHAR(64);
ALTER TABLE agents ADD INDEX idx_state (state);

-- ========================================
-- 2. 修复 agent_configs 表（关键！）
-- ========================================
ALTER TABLE agent_configs ADD COLUMN provider JSON;
ALTER TABLE agent_configs ADD COLUMN cwd VARCHAR(512);
ALTER TABLE agent_configs ADD COLUMN sandbox_policy VARCHAR(32) DEFAULT 'READ_ONLY';
ALTER TABLE agent_configs ADD COLUMN user_instructions TEXT;
ALTER TABLE agent_configs ADD COLUMN base_instructions TEXT;
ALTER TABLE agent_configs ADD COLUMN reasoning_effort VARCHAR(32) DEFAULT 'MEDIUM';
ALTER TABLE agent_configs ADD COLUMN reasoning_summary VARCHAR(32) DEFAULT 'AUTO';
ALTER TABLE agent_configs ADD COLUMN compact_prompt TEXT;
ALTER TABLE agent_configs ADD COLUMN model_overrides JSON;
ALTER TABLE agent_configs ADD COLUMN session_source JSON;
ALTER TABLE agent_configs ADD COLUMN is_template TINYINT(1) DEFAULT 0;
ALTER TABLE agent_configs ADD COLUMN tags VARCHAR(255);
ALTER TABLE agent_configs ADD COLUMN description TEXT;
ALTER TABLE agent_configs ADD COLUMN metadata JSON;
ALTER TABLE agent_configs ADD COLUMN last_used_at DATETIME;
ALTER TABLE agent_configs ADD COLUMN created_by VARCHAR(64);
```

**注意**: 如果某条 SQL 报错 "Duplicate column name"，说明该字段已存在，**跳过即可**。

---

## ✅ 验证修复

执行以下命令验证：

```sql
-- 查看 agents 表结构
DESCRIBE agents;

-- 查看 agent_configs 表结构
DESCRIBE agent_configs;
```

**agents 表应该包含**:
- agent_name ✅
- description ✅
- priority ✅
- state ✅
- full_history ✅
- last_used_at ✅
- created_by ✅

**agent_configs 表应该包含**:
- provider ✅
- cwd ✅
- sandbox_policy ✅
- user_instructions ✅
- base_instructions ✅
- reasoning_effort ✅
- reasoning_summary ✅
- compact_prompt ✅
- model_overrides ✅
- session_source ✅
- is_template ✅
- tags ✅
- description ✅
- metadata ✅
- last_used_at ✅
- created_by ✅

---

## 🔄 重启服务

修复数据库后，**必须重启** agentoz-server：

```bash
# Docker
docker restart agentoz-server

# 或 systemd
systemctl restart agentoz-server

# 或 kill + 重启 Java 进程
```

---

## 📁 文件清单

所有文件都在 `docs/database/` 目录：

1. **migration_complete.sql** - 完整自动化迁移脚本（推荐使用）
2. **migration_urgent_manual.sql** - 手动执行版（紧急备用）
3. **schema.sql** - 最新版完整表结构定义
4. **URGENT_FIX_GUIDE.md** - 快速参考指南

---

## 🔍 详细对比

### agents 表缺失字段

| Entity 字段 | 数据库（修复前） | 状态 |
|------------|-----------------|------|
| agentName | ❌ 缺失 | 已添加 |
| description | ❌ 缺失 | 已添加 |
| priority | ❌ 缺失 | 已添加 |
| state | status (不同) | 已统一 |
| fullHistory | ❌ 缺失 | 已添加 |
| lastUsedAt | ❌ 缺失 | 已添加 |
| createdBy | ❌ 缺失 | 已添加 |

### agent_configs 表缺失字段

| Entity 字段 | 数据库（修复前） | 状态 |
|------------|-----------------|------|
| provider | ❌ 缺失 | **关键** |
| cwd | ❌ 缺失 | **关键** |
| sandboxPolicy | ❌ 缺失 | 已添加 |
| userInstructions | ❌ 缺失 | **关键** |
| baseInstructions | ❌ 缺失 | **关键** |
| reasoningEffort | ❌ 缺失 | 已添加 |
| reasoningSummary | ❌ 缺失 | 已添加 |
| compactPrompt | ❌ 缺失 | 已添加 |
| modelOverrides | ❌ 缺失 | 已添加 |
| sessionSource | ❌ 缺失 | 已添加 |
| isTemplate | isActive (不同) | 已统一 |
| tags | ❌ 缺失 | 已添加 |
| lastUsedAt | ❌ 缺失 | 已添加 |
| createdBy | ❌ 缺失 | 已添加 |

---

## 🎯 执行后的预期结果

1. ✅ **agents 表**: 所有字段与 AgentEntity 类完全匹配
2. ✅ **agent_configs 表**: 所有字段与 AgentConfigEntity 类完全匹配
3. ✅ **创建会话成功**: 不再报 "Unknown column" 错误
4. ✅ **创建 Agent 成功**: AgentManagerServiceImpl 能正常工作

---

## ⚠️ 常见问题

### Q1: 执行 SQL 时报错 "Duplicate column name"
**A**: 字段已存在，跳过该条 SQL 继续执行下一条即可。

### Q2: 执行后还是报错
**A**: 检查以下几点：
1. 确认修改的是正确的数据库
2. 确认已重启应用
3. 查看完整错误日志，确认是哪个字段缺失

### Q3: 需要回滚吗？
**A**: 不需要。我们只是添加缺失字段，不影响现有数据。

---

## 📞 技术支持

如果修复后还有问题，请提供以下信息：

1. 执行 `DESCRIBE agents;` 和 `DESCRIBE agent_configs;` 的输出
2. 完整的错误堆栈
3. Entity 类定义路径

---

## ✨ 总结

这次修复涉及两个表：
- **agents 表**: 7 个缺失字段
- **agent_configs 表**: 15 个缺失字段

使用 `migration_complete.sql` 可以一键修复所有问题！
