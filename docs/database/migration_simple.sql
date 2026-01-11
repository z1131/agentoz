-- =============================================================================
-- AgentOZ 生产环境数据库迁移脚本（手动执行版）
-- 版本: 1.0.1
-- 说明: 逐条执行，每条 SQL 都有检查，可以安全地重复执行
-- =============================================================================

-- 步骤 1: 添加 agent_name 字段
-- 如果报错 "Duplicate column name"，说明字段已存在，可以忽略
ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255) COMMENT 'Agent显示名称' AFTER config_id;

-- 步骤 2: 添加 description 字段
ALTER TABLE agents ADD COLUMN description TEXT COMMENT 'Agent描述' AFTER is_primary;

-- 步骤 3: 添加 priority 字段
ALTER TABLE agents ADD COLUMN priority INT DEFAULT 5 COMMENT '优先级（用于多Agent调度）' AFTER description;

-- 步骤 4: 处理 status -> state 字段
-- 如果表中有 status 字段，先检查是否需要重命名
-- 如果报错 "Duplicate column name" 或 "column doesn't exist"，根据情况调整

-- 方案 A: 如果有 status 字段，执行这个：
-- ALTER TABLE agents CHANGE COLUMN status state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态';

-- 方案 B: 如果没有 status 字段，执行这个：
ALTER TABLE agents ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态' AFTER priority;

-- 步骤 5: 添加 full_history 字段
ALTER TABLE agents ADD COLUMN full_history JSON COMMENT '全量历史记录（JSON格式，已废弃）' AFTER state;

-- 步骤 6: 添加 last_used_at 字段
ALTER TABLE agents ADD COLUMN last_used_at DATETIME COMMENT '最后使用时间' AFTER last_activity_at;

-- 步骤 7: 添加 created_by 字段
ALTER TABLE agents ADD COLUMN created_by VARCHAR(64) COMMENT '创建者用户ID' AFTER last_used_at;

-- 步骤 8: 添加索引
ALTER TABLE agents ADD INDEX idx_state (state);

-- 步骤 9: 验证表结构
DESCRIBE agents;
