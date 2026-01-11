# 数据库字段不匹配问题修复

## 问题描述

部署时出现以下错误：

```
Error querying database. Cause: java.sql.SQLSyntaxErrorException: Unknown column 'agent_name' in 'field list'
```

**错误原因**: MyBatis-Plus 生成的 SQL 查询中包含了 `agent_name` 字段，但数据库表中没有该字段。

---

## 根本原因

数据库表结构与 Entity 类的字段定义不匹配。具体不匹配的字段如下：

| Entity 字段 | 数据库字段（修复前） | 状态 |
|------------|-------------------|------|
| agentName | ❌ 缺失 | 缺失 |
| isPrimary | agent_type (错误映射) | 字段名错误 |
| description | ❌ 缺失 | 缺失 |
| priority | ❌ 缺失 | 缺失 |
| state | status (字段名不同) | 字段名不同 |
| fullHistory | ❌ 缺失 | 缺失 |
| lastUsedAt | ❌ 缺失 | 缺失 |
| createdBy | ❌ 缺失 | 缺失 |

---

## 修复内容

### 1. 更新 schema.sql

**文件**: `/Users/zhangzihao/通用智能体/重构项目/agentoz/docs/database/schema.sql`

**修改内容**:

```sql
-- 添加业务属性字段
agent_name VARCHAR(255) COMMENT 'Agent显示名称',
description TEXT COMMENT 'Agent描述',
priority INT DEFAULT 5 COMMENT '优先级（用于多Agent调度）',

-- 修改状态字段名（从 status 改为 state）
state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态',

-- 添加废弃字段（保留兼容性）
full_history JSON COMMENT '全量历史记录（JSON格式，已废弃）',

-- 添加时间戳字段
last_used_at DATETIME COMMENT '最后使用时间',

-- 添加创建者字段
created_by VARCHAR(64) COMMENT '创建者用户ID',
```

### 2. 修复 AgentEntity 字段映射

**文件**: `/Users/zhangzihao/通用智能体/重构项目/agentoz/agentoz-server/src/main/java/com/deepknow/agentoz/model/AgentEntity.java`

**修改前**:
```java
private String agentName;  // 没有 @TableField 注解

@TableField("agent_type")  // 错误的字段名映射
private Boolean isPrimary;
```

**修改后**:
```java
@TableField("agent_name")  // 添加正确的字段映射
private String agentName;

@TableField("is_primary")  // 修正字段映射
private Boolean isPrimary;
```

### 3. 创建数据库迁移脚本

**文件**: `/Users/zhangzihao/通用智能体/重构项目/agentoz/docs/database/migration_add_missing_fields.sql`

**功能**:
- 在现有数据库上添加缺失的字段
- 处理 status → state 的字段重命名
- 添加缺失的索引
- 包含回滚脚本

---

## 如何应用修复

### 方案 1: 全新部署（推荐）

如果你创建的是全新的数据库，直接使用更新后的 `schema.sql`：

```bash
mysql -u your_user -p your_database < docs/database/schema.sql
```

### 方案 2: 现有数据库迁移

如果你已经有生产数据，使用迁移脚本：

```bash
mysql -u your_user -p your_database < docs/database/migration_add_missing_fields.sql
```

**迁移脚本会自动**:
- ✅ 检查字段是否存在，避免重复添加
- ✅ 智能处理 status → state 的重命名
- ✅ 添加缺失的索引
- ✅ 不影响现有数据

### 方案 3: 手动执行（不推荐）

如果需要手动执行 SQL，参考以下语句：

```sql
-- 添加缺失字段
ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN description TEXT;
ALTER TABLE agents ADD COLUMN priority INT DEFAULT 5;
ALTER TABLE agents ADD COLUMN full_history JSON;
ALTER TABLE agents ADD COLUMN last_used_at DATETIME;
ALTER TABLE agents ADD COLUMN created_by VARCHAR(64);

-- 重命名字段（如果存在 status）
ALTER TABLE agents CHANGE COLUMN status state VARCHAR(32);

-- 添加索引
ALTER TABLE agents ADD INDEX idx_state (state);
```

---

## 验证修复

### 1. 检查表结构

```sql
DESCRIBE agents;
```

**预期输出**应包含以下字段：
- agent_name
- is_primary
- description
- priority
- state (不是 status)
- full_history
- active_context
- state_description
- interaction_count
- last_interaction_type
- last_interaction_at
- last_activity_at
- last_used_at
- created_by

### 2. 检查索引

```sql
SHOW INDEX FROM agents;
```

**预期输出**应包含以下索引：
- idx_agent_id
- idx_conversation_id
- idx_config_id
- idx_is_primary
- idx_state
- idx_created_at
- idx_conversation_primary

### 3. 测试查询

```java
// 在 AgentExecutionServiceImpl 中测试
List<AgentEntity> agents = agentRepository.selectList(
    new LambdaQueryWrapper<AgentEntity>()
        .eq(AgentEntity::getConversationId, "conv-123")
);
```

应该不再报错 `Unknown column 'agent_name'`。

---

## 预防措施

为了避免将来再次出现此类问题，建议：

### 1. 使用 @TableField 注解

在 Entity 类中，所有与数据库字段名不同的 Java 字段都应该使用 `@TableField` 注解：

```java
@TableField("db_field_name")
private String javaFieldName;
```

### 2. 保持 schema.sql 与 Entity 同步

每次修改 Entity 类时，同步更新 `schema.sql`：

- 新增字段 → 添加到 schema.sql
- 修改字段 → 更新 schema.sql
- 删除字段 → 在 schema.sql 中标记为 `@Deprecated` 或创建迁移脚本

### 3. 编写单元测试

为每个 Entity 创建单元测试，验证字段映射：

```java
@Test
public void testAgentEntityFieldMapping() {
    AgentEntity entity = new AgentEntity();
    entity.setAgentName("测试Agent");

    // 验证能够正确保存和查询
    agentRepository.insert(entity);

    AgentEntity found = agentRepository.selectById(entity.getId());
    assertEquals("测试Agent", found.getAgentName());
}
```

### 4. 使用 Flyway 或 Liquibase

考虑使用数据库迁移工具（如 Flyway 或 Liquibase）来管理数据库版本：

```java
// V1.0.1__add_missing_fields.sql
ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255);
```

这样可以确保数据库版本与代码版本同步。

---

## 总结

✅ **已修复**:
- 更新了 schema.sql，添加了所有缺失字段
- 修正了 AgentEntity 的字段映射
- 创建了数据库迁移脚本
- 编译通过

⏳ **待执行**:
- 在生产环境执行迁移脚本
- 验证所有字段映射正确
- 测试 Agent 创建和查询功能

📚 **相关文件**:
- schema.sql: `/Users/zhangzihao/通用智能体/重构项目/agentoz/docs/database/schema.sql`
- migration: `/Users/zhangzihao/通用智能体/重构项目/agentoz/docs/database/migration_add_missing_fields.sql`
- Entity: `/Users/zhangzihao/通用智能体/重构项目/agentoz/agentoz-server/src/main/java/com/deepknow/agentoz/model/AgentEntity.java`
