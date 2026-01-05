-- ===================================
-- Agent Platform 数据库表定义
-- ===================================

-- 1. 会话表
CREATE TABLE IF NOT EXISTS `sessions` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `session_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID（业务主键）',
  `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
  `title` VARCHAR(255) NOT NULL COMMENT '会话标题',
  `primary_agent_id` VARCHAR(64) COMMENT '主Agent的ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE, INACTIVE, CLOSED',
  `total_message_count` INT DEFAULT 0 COMMENT '总消息数',
  `total_tokens_used` BIGINT DEFAULT 0 COMMENT '总Token消耗',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_activity_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 2. Agent 表
CREATE TABLE IF NOT EXISTS `agents` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `agent_id` VARCHAR(64) NOT NULL UNIQUE COMMENT 'Agent ID（业务主键）',
  `session_id` VARCHAR(64) NOT NULL COMMENT '所属会话ID',
  `agent_name` VARCHAR(128) NOT NULL COMMENT 'Agent名称（会话内唯一）',
  `agent_type` VARCHAR(32) NOT NULL COMMENT 'Agent类型：PRIMARY, SUB',
  `system_prompt` TEXT COMMENT '系统提示词',
  `mcp_config` TEXT COMMENT 'MCP服务器配置（JSON格式）',
  `config` TEXT COMMENT 'Agent配置（JSON格式）',
  `context` TEXT COMMENT '历史记录（JSON格式）',
  `prompt_tokens` INT DEFAULT 0 COMMENT 'Prompt tokens（最后一次调用）',
  `completion_tokens` INT DEFAULT 0 COMMENT 'Completion tokens（最后一次调用）',
  `total_tokens` INT DEFAULT 0 COMMENT '总tokens（累计）',
  `max_tokens` INT DEFAULT 4096 COMMENT '最大tokens（上下文窗口大小）',
  `token_count_estimated` INT DEFAULT 0 COMMENT '估算的当前token数',
  `state` VARCHAR(32) DEFAULT 'IDLE' COMMENT 'Agent状态：IDLE, THINKING, CALLING, ERROR',
  `version` INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_used_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后使用时间',
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_agent_name` (`agent_name`),
  INDEX `idx_state` (`state`),
  UNIQUE KEY `uk_session_agent_name` (`session_id`, `agent_name`),
  CONSTRAINT `fk_agent_session` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent表';

-- 3. 用户消息表
CREATE TABLE IF NOT EXISTS `user_messages` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `message_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '消息ID（业务主键）',
  `session_id` VARCHAR(64) NOT NULL COMMENT '所属会话ID',
  `agent_id` VARCHAR(64) NOT NULL COMMENT '关联的Agent ID',
  `role` VARCHAR(32) NOT NULL COMMENT '角色：user, assistant, system',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `is_visible` BOOLEAN DEFAULT TRUE COMMENT '是否对用户可见',
  `sequence` INT NOT NULL COMMENT '序号（在会话中的顺序）',
  `tokens` INT DEFAULT 0 COMMENT 'Token数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_sequence` (`sequence`),
  CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`session_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_message_agent` FOREIGN KEY (`agent_id`) REFERENCES `agents` (`agent_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息表';

-- 4. Agent调用日志表
CREATE TABLE IF NOT EXISTS `agent_call_logs` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `call_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '调用ID（业务主键）',
  `session_id` VARCHAR(64) NOT NULL COMMENT '所属会话ID',
  `from_agent_id` VARCHAR(64) NOT NULL COMMENT '调用者Agent ID',
  `to_agent_id` VARCHAR(64) COMMENT '被调用者Agent ID',
  `call_type` VARCHAR(32) NOT NULL COMMENT '调用类型：AGENT_TO_AGENT, AGENT_TO_TOOL, TOOL_RESPONSE',
  `target_name` VARCHAR(128) COMMENT '被调用者的名称',
  `request_content` TEXT COMMENT '调用内容',
  `response_content` TEXT COMMENT '响应内容',
  `tokens_used` INT DEFAULT 0 COMMENT 'Token消耗',
  `status` VARCHAR(32) NOT NULL COMMENT '状态：SUCCESS, ERROR, TIMEOUT',
  `error_message` TEXT COMMENT '错误信息',
  `started_at` DATETIME NOT NULL COMMENT '开始时间',
  `completed_at` DATETIME COMMENT '完成时间',
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_from_agent` (`from_agent_id`),
  INDEX `idx_to_agent` (`to_agent_id`),
  INDEX `idx_started_at` (`started_at`),
  CONSTRAINT `fk_call_log_session` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent调用日志表';CREATE TABLE IF NOT EXISTS session_resources (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  resource_key VARCHAR(128) NOT NULL,
  content LONGTEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_session_resource (session_id, resource_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话共享资源表';
