-- =============================================================================
-- AgentOZ 数据库迁移脚本：添加缺失字段
-- 版本: 1.0.1
-- 更新时间: 2025-01-11
-- 说明: 修复 agents 表字段与 Entity 类不匹配的问题
-- =============================================================================

-- 查看当前表结构（可选）
-- DESCRIBE agents;

-- =============================================================================
-- 1. 添加缺失的字段
-- =============================================================================

-- 添加 agent_name 字段
ALTER TABLE agents ADD COLUMN IF NOT EXISTS agent_name VARCHAR(255) COMMENT 'Agent显示名称，如: "代码助手", "数据分析专家"' AFTER config_id;

-- 添加 description 字段
ALTER TABLE agents ADD COLUMN IF NOT EXISTS description TEXT COMMENT 'Agent描述' AFTER is_primary;

-- 添加 priority 字段
ALTER TABLE agents ADD COLUMN IF NOT EXISTS priority INT DEFAULT 5 COMMENT '优先级（用于多Agent调度），范围1-10，数字越大优先级越高' AFTER description;

-- 重命名 status 字段为 state（如果存在 status 字段）
-- 注意：MySQL 不直接支持 ALTER TABLE ... RENAME COLUMN 的 IF EXISTS 语法
-- 所以需要先检查字段是否存在
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'agents'
    AND COLUMN_NAME = 'status'
);

-- 如果 status 字段存在，则重命名为 state
SET @sql = IF(@column_exists > 0,
    'ALTER TABLE agents CHANGE COLUMN status state VARCHAR(32) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''Agent状态: ACTIVE, INACTIVE, ERROR''',
    'SELECT ''Column status does not exist, skipping rename'' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 full_history 字段（已废弃）
ALTER TABLE agents ADD COLUMN IF NOT EXISTS full_history JSON COMMENT '全量历史记录（JSON格式，已废弃，建议使用 activeContext）' AFTER state;

-- 添加 last_used_at 字段
ALTER TABLE agents ADD COLUMN IF NOT EXISTS last_used_at DATETIME COMMENT '最后使用时间' AFTER last_activity_at;

-- 添加 created_by 字段
ALTER TABLE agents ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) COMMENT '创建者用户ID' AFTER last_used_at;

-- =============================================================================
-- 2. 添加缺失的索引
-- =============================================================================

-- 添加 state 字段的索引
ALTER TABLE agents ADD INDEX IF NOT EXISTS idx_state (state);

-- =============================================================================
-- 3. 验证修改
-- =============================================================================

-- 查看修改后的表结构
DESCRIBE agents;

-- 查看所有索引
SHOW INDEX FROM agents;

-- =============================================================================
-- 4. 数据修复（可选）
-- =============================================================================

-- 如果有现有的 Agent 记录没有 agent_name，可以设置默认值
-- UPDATE agents SET agent_name = '未命名Agent' WHERE agent_name IS NULL OR agent_name = '';

-- =============================================================================
-- 回滚脚本（如果需要回滚，请执行以下语句）
-- =============================================================================

-- /*
-- ALTER TABLE agents DROP COLUMN agent_name;
-- ALTER TABLE agents DROP COLUMN description;
-- ALTER TABLE agents DROP COLUMN priority;
-- ALTER TABLE agents CHANGE COLUMN state status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态: ACTIVE, INACTIVE, ERROR';
-- ALTER TABLE agents DROP COLUMN full_history;
-- ALTER TABLE agents DROP COLUMN last_used_at;
-- ALTER TABLE agents DROP COLUMN created_by;
-- ALTER TABLE agents DROP INDEX idx_state;
-- */
