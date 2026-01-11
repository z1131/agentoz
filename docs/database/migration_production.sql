-- =============================================================================
-- AgentOZ 生产环境数据库迁移脚本（简化版）
-- 版本: 1.0.1
-- 说明: 添加 agents 表缺失的字段
-- 使用方法: mysql -u用户名 -p密码 数据库名 < migration_production.sql
-- =============================================================================

-- 设置字符集
SET NAMES utf8mb4;

-- 选择数据库（请根据实际情况修改）
USE agentoz;

-- =============================================================================
-- 1. 添加缺失的字段（使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS 的替代方案）
-- =============================================================================

-- 由于 MySQL 5.7 及以下版本不支持 ADD COLUMN IF NOT EXISTS
-- 我们使用存储过程的方式来安全地添加字段

DELIMITER $$

DROP PROCEDURE IF EXISTS safe_add_column$$

CREATE PROCEDURE safe_add_column()
BEGIN
    -- 添加 agent_name 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'agent_name'
    ) THEN
        ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255) COMMENT 'Agent显示名称' AFTER config_id;
    END IF;

    -- 添加 description 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'description'
    ) THEN
        ALTER TABLE agents ADD COLUMN description TEXT COMMENT 'Agent描述' AFTER is_primary;
    END IF;

    -- 添加 priority 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'priority'
    ) THEN
        ALTER TABLE agents ADD COLUMN priority INT DEFAULT 5 COMMENT '优先级（用于多Agent调度）' AFTER description;
    END IF;

    -- 处理 status -> state 的字段重命名
    -- 如果存在 status 字段且不存在 state 字段，则重命名
    IF EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'status'
    ) AND NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'state'
    ) THEN
        ALTER TABLE agents CHANGE COLUMN status state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态: ACTIVE, INACTIVE, ERROR';
    END IF;

    -- 如果既没有 status 也没有 state，则添加 state 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'state'
    ) AND NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'status'
    ) THEN
        ALTER TABLE agents ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态: ACTIVE, INACTIVE, ERROR' AFTER priority;
    END IF;

    -- 添加 full_history 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'full_history'
    ) THEN
        ALTER TABLE agents ADD COLUMN full_history JSON COMMENT '全量历史记录（JSON格式，已废弃）' AFTER state;
    END IF;

    -- 添加 last_used_at 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'last_used_at'
    ) THEN
        ALTER TABLE agents ADD COLUMN last_used_at DATETIME COMMENT '最后使用时间' AFTER last_activity_at;
    END IF;

    -- 添加 created_by 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND COLUMN_NAME = 'created_by'
    ) THEN
        ALTER TABLE agents ADD COLUMN created_by VARCHAR(64) COMMENT '创建者用户ID' AFTER last_used_at;
    END IF;

END$$

DELIMITER ;

-- 执行存储过程
CALL safe_add_column();

-- 删除存储过程
DROP PROCEDURE IF EXISTS safe_add_column;

-- =============================================================================
-- 2. 添加缺失的索引
-- =============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS safe_add_index$$

CREATE PROCEDURE safe_add_index()
BEGIN
    -- 添加 state 字段的索引
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agents'
        AND INDEX_NAME = 'idx_state'
    ) THEN
        ALTER TABLE agents ADD INDEX idx_state (state);
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL safe_add_index();

-- 删除存储过程
DROP PROCEDURE IF EXISTS safe_add_index;

-- =============================================================================
-- 3. 验证修改
-- =============================================================================

-- 查看表结构
SELECT '=== agents 表结构 ===' AS '';
DESCRIBE agents;

-- 查看索引
SELECT '=== agents 表索引 ===' AS '';
SHOW INDEX FROM agents;

-- =============================================================================
-- 4. 完成
-- =============================================================================

SELECT '✅ 数据库迁移完成！' AS '';

-- 检查关键字段是否存在
SELECT
    CASE
        WHEN EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'agents'
                    AND COLUMN_NAME = 'agent_name')
        THEN '✅ agent_name 字段已添加'
        ELSE '❌ agent_name 字段缺失'
    END AS check_result;

SELECT
    CASE
        WHEN EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'agents'
                    AND COLUMN_NAME = 'description')
        THEN '✅ description 字段已添加'
        ELSE '❌ description 字段缺失'
    END AS check_result;

SELECT
    CASE
        WHEN EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'agents'
                    AND COLUMN_NAME = 'priority')
        THEN '✅ priority 字段已添加'
        ELSE '❌ priority 字段缺失'
    END AS check_result;

SELECT
    CASE
        WHEN EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'agents'
                    AND COLUMN_NAME = 'state')
        THEN '✅ state 字段已存在'
        ELSE '❌ state 字段缺失'
    END AS check_result;
