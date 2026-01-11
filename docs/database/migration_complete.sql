-- =============================================================================
-- AgentOZ 生产环境完整数据库迁移脚本
-- 版本: 1.0.2
-- 说明: 修复 agents 和 agent_configs 两个表的所有缺失字段
-- 使用方法: mysql -u用户名 -p密码 数据库名 < migration_complete.sql
-- =============================================================================

-- 设置字符集
SET NAMES utf8mb4;

-- 选择数据库（请根据实际情况修改）
-- USE agentoz;

-- =============================================================================
-- 第一部分：修复 agents 表
-- =============================================================================

SELECT '=== 开始修复 agents 表 ===' AS '';

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_agents_table$$

CREATE PROCEDURE migrate_agents_table()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
        -- 显示错误信息但继续执行
        GET DIAGNOSTICS CONDITION 1 @sqlstate = RETURNED_SQLSTATE, @errno = MYSQL_ERRNO, @text = MESSAGE_TEXT;
        SELECT CONCAT('警告: ', @errno, ' - ', @text) AS warning_message;
    END;

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

-- 执行 agents 表迁移
CALL migrate_agents_table();
DROP PROCEDURE IF EXISTS migrate_agents_table;

SELECT '✅ agents 表修复完成' AS '';

-- =============================================================================
-- 第二部分：修复 agent_configs 表
-- =============================================================================

SELECT '=== 开始修复 agent_configs 表 ===' AS '';

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_agent_configs_table$$

CREATE PROCEDURE migrate_agent_configs_table()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
        -- 显示错误信息但继续执行
        GET DIAGNOSTICS CONDITION 1 @sqlstate = RETURNED_SQLSTATE, @errno = MYSQL_ERRNO, @text = MESSAGE_TEXT;
        SELECT CONCAT('警告: ', @errno, ' - ', @text) AS warning_message;
    END;

    -- 添加 provider 字段 (JSON 类型，存储 ProviderConfigVO)
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'provider'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN provider JSON COMMENT '模型提供商配置（ProviderConfigVO的JSON格式）' AFTER config_name;
    END IF;

    -- 添加 cwd 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'cwd'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN cwd VARCHAR(512) COMMENT '工作目录（绝对路径）' AFTER llm_model;
    END IF;

    -- 添加 sandbox_policy 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'sandbox_policy'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN sandbox_policy VARCHAR(32) DEFAULT 'READ_ONLY' COMMENT '沙箱策略: READ_ONLY, SANDBOXED, INSECURE' AFTER approval_policy;
    END IF;

    -- 添加 user_instructions 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'user_instructions'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN user_instructions TEXT COMMENT '用户指令（给Agent的业务级指令）' AFTER developer_instructions;
    END IF;

    -- 添加 base_instructions 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'base_instructions'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN base_instructions TEXT COMMENT '基础指令模板（覆盖默认行为模板）' AFTER user_instructions;
    END IF;

    -- 添加 reasoning_effort 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'reasoning_effort'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN reasoning_effort VARCHAR(32) DEFAULT 'MEDIUM' COMMENT '推理强度: REASONING_NONE, MINIMAL, LOW, MEDIUM, HIGH' AFTER system_instructions;
    END IF;

    -- 添加 reasoning_summary 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'reasoning_summary'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN reasoning_summary VARCHAR(32) DEFAULT 'AUTO' COMMENT '推理摘要模式: AUTO, CONCISE, DETAILED, REASONING_SUMMARY_NONE' AFTER reasoning_effort;
    END IF;

    -- 添加 compact_prompt 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'compact_prompt'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN compact_prompt TEXT COMMENT '压缩提示词覆盖' AFTER reasoning_summary;
    END IF;

    -- 添加 model_overrides 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'model_overrides'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN model_overrides JSON COMMENT '模型能力覆盖配置（ModelOverridesVO的JSON格式）' AFTER compact_prompt;
    END IF;

    -- 添加 session_source 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'session_source'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN session_source JSON COMMENT '会话来源标识（SessionSourceVO的JSON格式）' AFTER model_overrides;
    END IF;

    -- 添加 is_template 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'is_template'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN is_template TINYINT(1) DEFAULT 0 COMMENT '是否为预设模板: 1=是, 0=否' AFTER session_source;
    END IF;

    -- 添加 tags 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'tags'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN tags VARCHAR(255) COMMENT '配置标签（逗号分隔）' AFTER is_template;
    END IF;

    -- 添加 description 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'description'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN description TEXT COMMENT '配置描述' AFTER tags;
    END IF;

    -- 添加 metadata 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'metadata'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN metadata JSON COMMENT '扩展元数据（JSON格式）' AFTER description;
    END IF;

    -- 添加 last_used_at 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'last_used_at'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN last_used_at DATETIME COMMENT '最后使用时间' AFTER updated_at;
    END IF;

    -- 添加 created_by 字段
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agent_configs'
        AND COLUMN_NAME = 'created_by'
    ) THEN
        ALTER TABLE agent_configs ADD COLUMN created_by VARCHAR(64) COMMENT '创建者用户ID' AFTER last_used_at;
    END IF;

END$$

DELIMITER ;

-- 执行 agent_configs 表迁移
CALL migrate_agent_configs_table();
DROP PROCEDURE IF EXISTS migrate_agent_configs_table;

SELECT '✅ agent_configs 表修复完成' AS '';

-- =============================================================================
-- 第三部分：验证修复结果
-- =============================================================================

SELECT '=== 验证 agents 表 ===' AS '';
DESCRIBE agents;

SELECT '=== 验证 agent_configs 表 ===' AS '';
DESCRIBE agent_configs;

-- =============================================================================
-- 第四部分：检查关键字段
-- =============================================================================

SELECT '=== 检查 agents 表关键字段 ===' AS '';
SELECT
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'agent_name'
        ) THEN '✅'
        ELSE '❌'
    END AS agent_name,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'description'
        ) THEN '✅'
        ELSE '❌'
    END AS description,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agents'
            AND COLUMN_NAME = 'state'
        ) THEN '✅'
        ELSE '❌'
    END AS state;

SELECT '=== 检查 agent_configs 表关键字段 ===' AS '';
SELECT
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agent_configs'
            AND COLUMN_NAME = 'provider'
        ) THEN '✅'
        ELSE '❌'
    END AS provider,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agent_configs'
            AND COLUMN_NAME = 'cwd'
        ) THEN '✅'
        ELSE '❌'
    END AS cwd,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agent_configs'
            AND COLUMN_NAME = 'sandbox_policy'
        ) THEN '✅'
        ELSE '❌'
    END AS sandbox_policy,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agent_configs'
            AND COLUMN_NAME = 'user_instructions'
        ) THEN '✅'
        ELSE '❌'
    END AS user_instructions,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'agent_configs'
            AND COLUMN_NAME = 'base_instructions'
        ) THEN '✅'
        ELSE '❌'
    END AS base_instructions;

-- =============================================================================
-- 完成
-- =============================================================================

SELECT '========================================' AS '';
SELECT '✅ 数据库迁移完成！' AS '';
SELECT '========================================' AS '';
SELECT '如果所有字段都显示 ✅，则迁移成功！' AS '';
SELECT '请重启应用以使更改生效' AS '';
