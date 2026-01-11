# AgentConfigEntity 字段对应关系检查

## Entity 字段 → 数据库字段映射

| Java 字段 | Java 类型 | @TableField 注解 | 数据库字段 | 数据库类型 | 状态 |
|----------|----------|-----------------|-----------|-----------|------|
| id | Long | (主键) | id | BIGINT AUTO_INCREMENT | ✅ |
| configId | String | - | config_id | VARCHAR(64) | ✅ |
| configName | String | - | config_name | VARCHAR(255) | ✅ |
| **provider** | ProviderConfigVO | - | **provider** | JSON | ✅ |
| **llmModel** | String | @TableField("model") | **model** | VARCHAR(128) | ⚠️ 需要检查 |
| cwd | String | - | cwd | VARCHAR(512) | ✅ |
| approvalPolicy | String | - | approval_policy | VARCHAR(32) | ✅ |
| sandboxPolicy | String | - | sandbox_policy | VARCHAR(32) | ✅ |
| developerInstructions | String | - | developer_instructions | TEXT | ✅ |
| userInstructions | String | - | user_instructions | TEXT | ✅ |
| baseInstructions | String | - | base_instructions | TEXT | ✅ |
| reasoningEffort | String | - | reasoning_effort | VARCHAR(32) | ✅ |
| reasoningSummary | String | - | reasoning_summary | VARCHAR(32) | ✅ |
| compactPrompt | String | - | compact_prompt | TEXT | ✅ |
| modelOverrides | ModelOverridesVO | - | model_overrides | JSON | ✅ |
| mcpServers | Map<String, McpServerConfigVO> | - | (不存储，使用 mcp_config_json) | - | ⚠️ |
| **mcpConfigJson** | String | - | **mcp_config_json** | JSON | ✅ |
| sessionSource | SessionSourceVO | - | session_source | JSON | ✅ |
| isTemplate | Boolean | - | is_template | TINYINT(1) | ✅ |
| tags | String | - | tags | VARCHAR(255) | ✅ |
| description | String | - | description | TEXT | ✅ |
| metadata | String | - | metadata | JSON | ✅ |
| createdAt | LocalDateTime | - | created_at | DATETIME | ✅ |
| updatedAt | LocalDateTime | - | updated_at | DATETIME | ✅ |
| lastUsedAt | LocalDateTime | - | last_used_at | DATETIME | ✅ |
| createdBy | String | - | created_by | VARCHAR(64) | ✅ |

## ⚠️ 关键问题

### 问题 1: llmModel 字段映射

**Entity 定义**:
```java
@TableField("model")
private String llmModel;
```

**数据库字段应该是**: `model` (不是 `llm_model`!)

### 问题 2: mcpServers vs mcpConfigJson

Entity 中有两个字段：
- `mcpServers` (Map类型，带 @TableField typeHandler)
- `mcpConfigJson` (String类型)

**实际使用的是 `mcpConfigJson`**，存储在数据库的 `mcp_config_json` 字段。

---

## 🔧 修复步骤

### 检查当前数据库字段名

```sql
-- 检查 model 相关的字段
SELECT COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME = 'agent_configs'
AND COLUMN_NAME IN ('model', 'llm_model');
```

### 如果存在 llm_model，需要重命名

```sql
-- 重命名字段
ALTER TABLE agent_configs CHANGE COLUMN llm_model model VARCHAR(128) NOT NULL COMMENT '模型名称';
```

### 如果不存在 model 字段，需要添加

```sql
-- 添加 model 字段
ALTER TABLE agent_configs ADD COLUMN model VARCHAR(128) NOT NULL COMMENT '模型名称' AFTER provider;
```

---

## ✅ 完整修复 SQL

```sql
-- 修复 model 字段
-- 情况1: 如果有 llm_model，先检查
-- ALTER TABLE agent_configs CHANGE COLUMN llm_model model VARCHAR(128);

-- 情况2: 如果没有 model 字段，添加
-- ALTER TABLE agent_configs ADD COLUMN model VARCHAR(128) AFTER provider;

-- 其他缺失字段
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
