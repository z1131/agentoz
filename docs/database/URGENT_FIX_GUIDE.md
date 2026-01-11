# ⚠️ 生产环境紧急修复指南

## 问题

生产环境报错：
```
Unknown column 'agent_name' in 'field list'
```

## 快速解决方案

### 方案 1: 一键执行（推荐）

直接在生产数据库执行此文件：

```bash
mysql -u你的用户名 -p你的密码 数据库名 < docs/database/migration_production.sql
```

### 方案 2: 手动逐条执行

如果方案 1 有问题，使用 `migration_simple.sql`：

1. 登录 MySQL：
```bash
mysql -u你的用户名 -p
```

2. 选择数据库：
```sql
use agentoz;  -- 或你的数据库名
```

3. 逐条执行以下 SQL（如果某条报错 "Duplicate column name"，说明字段已存在，跳过即可）：

```sql
-- 添加缺失的字段
ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN description TEXT;
ALTER TABLE agents ADD COLUMN priority INT DEFAULT 5;
ALTER TABLE agents ADD COLUMN state VARCHAR(32) DEFAULT 'ACTIVE';
ALTER TABLE agents ADD COLUMN full_history JSON;
ALTER TABLE agents ADD COLUMN last_used_at DATETIME;
ALTER TABLE agents ADD COLUMN created_by VARCHAR(64);

-- 添加索引
ALTER TABLE agents ADD INDEX idx_state (state);
```

4. 验证修复：
```sql
DESCRIBE agents;
```

应该能看到 `agent_name` 等字段。

### 方案 3: 如果有 status 字段

如果你的表有 `status` 字段而不是 `state`，需要先重命名：

```sql
-- 检查是否有 status 字段
SHOW COLUMNS FROM agents LIKE 'status';

-- 如果有，执行重命名
ALTER TABLE agents CHANGE COLUMN status state VARCHAR(32);
```

## 验证修复

执行诊断脚本：

```bash
mysql -u你的用户名 -p 数据库名 < docs/database/diagnose.sql
```

或者在 MySQL 中：

```sql
-- 检查 agent_name 字段是否存在
SELECT
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'agent_name'
        ) THEN '✅ agent_name 存在'
        ELSE '❌ agent_name 缺失'
    END AS check_result;
```

## 重启服务

修复数据库后，重启 agentoz-server：

```bash
# 如果使用 Docker
docker restart agentoz-server

# 如果使用进程管理工具
systemctl restart agentoz-server

# 或者直接重启 Java 进程
```

## 如果还有问题

1. 检查数据库连接配置：`application.yml`
2. 确认你修改的是正确的数据库
3. 查看完整的错误日志

## 相关文件

- **自动迁移脚本**: `migration_production.sql`
- **手动执行脚本**: `migration_simple.sql`
- **诊断脚本**: `diagnose.sql`
- **详细说明**: `field-mismatch-fix.md`
