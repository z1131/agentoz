-- =============================================================================
-- AgentOZ 数据库诊断脚本
-- 说明: 运行此脚本来检查当前数据库表结构是否正确
-- =============================================================================

-- 查看当前数据库
SELECT DATABASE() AS current_database;

-- 查看 agents 表结构
SELECT '=== 当前 agents 表结构 ===' AS '';
DESCRIBE agents;

-- 检查关键列是否存在
SELECT '=== 检查关键字段 ===' AS '';

SELECT
    'agent_name' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'agent_name'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

SELECT
    'description' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'description'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

SELECT
    'priority' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'priority'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

SELECT
    'state' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'state'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

SELECT
    'status' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'status'
        ) THEN '⚠️  存在（应该重命名为 state）'
        ELSE '✅ 不存在（正确）'
    END AS status;

SELECT
    'full_history' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'full_history'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

SELECT
    'last_used_at' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'last_used_at'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

SELECT
    'created_by' AS field_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'created_by'
        ) THEN '✅ 存在'
        ELSE '❌ 缺失 - 需要添加'
    END AS status;

-- 查看所有索引
SELECT '=== 当前索引 ===' AS '';
SHOW INDEX FROM agents;

-- 生成修复建议
SELECT '=== 修复建议 ===' AS '';
SELECT
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'agent_name'
        ) THEN '所有必需字段都存在，无需修复'
        ELSE '检测到缺失字段，请执行 migration_production.sql 或 migration_simple.sql'
    END AS fix_suggestion;
