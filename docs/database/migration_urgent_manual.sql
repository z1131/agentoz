-- =============================================================================
-- AgentOZ 紧急修复 - 手动执行版
-- 说明: 逐条执行，如果某条报错字段已存在，跳过即可
-- =============================================================================

-- ========================================
-- 1. 修复 agents 表
-- ========================================

ALTER TABLE agents ADD COLUMN agent_name VARCHAR(255);
ALTER TABLE agents ADD COLUMN description TEXT;
ALTER TABLE agents ADD COLUMN priority INT DEFAULT 5;

-- 如果有 status 字段，执行下面这条（否则跳过）
-- ALTER TABLE agents CHANGE COLUMN status state VARCHAR(32) DEFAULT 'ACTIVE';

-- 如果没有 state 字段，执行下面这条
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

-- ========================================
-- 3. 验证
-- ========================================

DESCRIBE agents;
DESCRIBE agent_configs;
