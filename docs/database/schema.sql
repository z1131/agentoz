-- AgentOZ 数据库 Schema 设计
-- 版本: 1.0.0
-- 更新时间: 2025-01-11

-- =============================================================================
-- 1. 会话表 (conversations)
-- =============================================================================
-- 存储会话级别的完整历史，包含所有用户输入和 Agent 返回

CREATE TABLE IF NOT EXISTS conversations (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',

    -- 业务标识
    conversation_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话唯一标识（对齐Codex-Agent的conversation_id）',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    business_code VARCHAR(64) COMMENT '业务线/应用编码',
    title VARCHAR(500) COMMENT '会话标题（可从对话中提取或用户指定）',

    -- Agent 关联
    primary_agent_id VARCHAR(64) COMMENT '该会话的主智能体ID',

    -- 状态管理
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, CLOSED, ARCHIVED',

    -- ⭐ 核心：完整的对话历史（JSON 数组格式）
    -- 存储所有 HistoryItem 的 JSON 数组，包含：
    -- - MessageItem: 用户消息和 Agent 响应
    -- - FunctionCallItem: Agent 调用工具的记录
    -- - FunctionCallOutputItem: 工具返回的结果
    history_context JSON NOT NULL COMMENT '对话历史JSON数组: [{"message":{...}}, {"function_call":{...}}, ...]',
    history_format VARCHAR(32) DEFAULT 'history_items_v1' COMMENT '历史格式版本，便于未来升级',

    -- 辅助字段（用于快速查询和展示）
    message_count INT NOT NULL DEFAULT 0 COMMENT '历史消息总数（用于快速判断上下文长度）',
    last_message_content TEXT COMMENT '最后一条消息内容（用于会话列表展示）',
    last_message_type VARCHAR(32) COMMENT '最后一条消息类型: message/function_call/function_call_output',
    last_message_at DATETIME COMMENT '最后一条消息的时间戳',

    -- 扩展字段
    metadata JSON COMMENT '扩展元数据（JSON格式），可存储任何自定义字段',

    -- 时间戳
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_activity_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活动时间',

    -- 索引
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_user_id (user_id),
    INDEX idx_business_code (business_code),
    INDEX idx_primary_agent_id (primary_agent_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_last_activity_at (last_activity_at),
    INDEX idx_user_status (user_id, status)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='会话表：存储完整的对话历史，包含所有用户输入和Agent响应';


-- =============================================================================
-- 2. Agent 表 (agents)
-- =============================================================================
-- 存储 Agent 实例及其交互历史

CREATE TABLE IF NOT EXISTS agents (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',

    -- 业务标识
    agent_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Agent唯一标识',
    conversation_id VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    config_id VARCHAR(64) NOT NULL COMMENT '关联的配置ID',

    -- 业务属性
    agent_name VARCHAR(255) COMMENT 'Agent显示名称，如: "代码助手", "数据分析专家"',
    is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为主智能体: 1=是, 0=否',
    description TEXT COMMENT 'Agent描述',
    priority INT DEFAULT 5 COMMENT '优先级（用于多Agent调度），范围1-10，数字越大优先级越高',

    -- Agent 状态
    state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Agent状态: ACTIVE, INACTIVE, ERROR',

    -- ⭐ 核心 1: Agent 级别的交互历史（JSON 数组格式）
    -- 存储与该 Agent 相关的所有交互，包含：
    -- - 用户直接发送给该 Agent 的消息
    -- - 该 Agent 的所有响应
    -- - 其他 Agent 调用该 Agent 的消息
    -- 更新策略：每次该 Agent 被调用和返回时都追加
    full_history JSON COMMENT '全量历史记录（JSON格式，已废弃，建议使用 activeContext）',
    active_context JSON NOT NULL COMMENT 'Agent交互历史JSON数组: [{"message":{...}}, {"function_call":{...}}, ...]',
    context_format VARCHAR(32) DEFAULT 'history_items_v1' COMMENT '上下文格式版本',

    -- ⭐ 核心 2: Agent 状态描述（新增）
    -- 记录 Agent 被调用时的输入摘要和执行结果摘要
    -- 更新策略：每次被调用和返回时都更新
    state_description TEXT COMMENT 'Agent当前状态的简要描述，例如："正在处理天气查询任务 | 已完成：北京晴天25°C"',

    -- 辅助字段
    interaction_count INT NOT NULL DEFAULT 0 COMMENT '该 Agent 的交互次数（调用+返回的总次数）',
    last_interaction_type VARCHAR(32) COMMENT '最后交互类型: input(被调用)/output(返回)',
    last_interaction_at DATETIME COMMENT '最后交互时间',

    -- 配置快照（用于审计）
    config_snapshot JSON COMMENT '创建时的配置快照（JSON格式）',

    -- 扩展字段
    metadata JSON COMMENT '扩展元数据（JSON格式）',

    -- 时间戳
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_activity_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活动时间',
    last_used_at DATETIME COMMENT '最后使用时间',

    -- 创建者
    created_by VARCHAR(64) COMMENT '创建者用户ID',

    -- 索引
    INDEX idx_agent_id (agent_id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_config_id (config_id),
    INDEX idx_is_primary (is_primary),
    INDEX idx_state (state),
    INDEX idx_created_at (created_at),
    INDEX idx_conversation_primary (conversation_id, is_primary),

    -- 外键约束
    FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Agent表：存储Agent实例及其交互历史和状态描述';


-- =============================================================================
-- 3. Agent 配置表 (agent_configs)
-- =============================================================================
-- 存储 Agent 的配置信息

CREATE TABLE IF NOT EXISTS agent_configs (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',

    -- 业务标识
    config_id VARCHAR(64) NOT NULL UNIQUE COMMENT '配置唯一标识',
    user_id VARCHAR(64) COMMENT '创建用户ID',

    -- 配置名称
    config_name VARCHAR(255) NOT NULL COMMENT '配置名称',
    description TEXT COMMENT '配置描述',

    -- LLM 配置
    llm_provider VARCHAR(64) NOT NULL COMMENT 'LLM提供商: openai, qwen, deepseek等',
    llm_model VARCHAR(128) NOT NULL COMMENT '模型名称，如: gpt-4o, qwen-plus',
    llm_api_key VARCHAR(512) COMMENT 'LLM API密钥（加密存储）',
    llm_base_url VARCHAR(512) COMMENT 'LLM API基础URL',

    -- MCP 配置（JSON 格式）
    -- 存储结构化的 MCP 服务器配置
    mcp_config_json JSON COMMENT 'MCP配置JSON: {"mcp_servers": {"server_name": {...}}}',

    -- 指令配置
    system_instructions TEXT COMMENT '系统级指令（覆盖默认）',
    user_instructions TEXT COMMENT '用户级指令',
    developer_instructions TEXT COMMENT '开发者级指令（最高优先级）',

    -- 策略配置
    approval_policy VARCHAR(32) DEFAULT 'AUTO_APPROVE' COMMENT '审批策略: AUTO_APPROVE, MANUAL_APPROVE, BLOCK_ALL',
    sandbox_policy VARCHAR(32) DEFAULT 'READ_ONLY' COMMENT '沙箱策略: READ_ONLY, SANDBOXED, INSECURE',

    -- 推理配置
    reasoning_effort VARCHAR(32) DEFAULT 'MEDIUM' COMMENT '推理努力程度: MINIMAL, LOW, MEDIUM, HIGH',
    reasoning_summary VARCHAR(32) DEFAULT 'AUTO' COMMENT '推理摘要模式: AUTO, CONCISE, DETAILED, NONE',

    -- 模型覆盖配置（JSON 格式）
    model_overrides JSON COMMENT '模型能力覆盖配置（JSON格式）',

    -- 状态
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 1=启用, 0=禁用',

    -- 扩展字段
    metadata JSON COMMENT '扩展元数据（JSON格式）',

    -- 时间戳
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX idx_config_id (config_id),
    INDEX idx_user_id (user_id),
    INDEX idx_is_active (is_active),
    INDEX idx_llm_provider (llm_provider)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Agent配置表：存储Agent的LLM、MCP、指令等配置信息';


-- =============================================================================
-- 初始化示例数据（可选）
-- =============================================================================

-- 插入一个默认配置示例
INSERT INTO agent_configs (
    config_id,
    user_id,
    config_name,
    description,
    llm_provider,
    llm_model,
    approval_policy,
    sandbox_policy
) VALUES (
    'cfg-default-001',
    'system',
    '默认配置',
    '系统默认的Agent配置，使用GPT-4o',
    'openai',
    'gpt-4o',
    'AUTO_APPROVE',
    'READ_ONLY'
) ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
