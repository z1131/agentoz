-- AgentOZ 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS agentoz DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE agentoz;

-- Agent定义表
CREATE TABLE IF NOT EXISTS `agent` (
    `id` VARCHAR(64) NOT NULL COMMENT 'Agent ID',
    `name` VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
    `system_prompt` TEXT NOT NULL COMMENT '系统提示词',
    `model_name` VARCHAR(64) DEFAULT 'qwen-max' COMMENT '模型名称',
    `tools` JSON DEFAULT NULL COMMENT '工具列表',
    `config` JSON DEFAULT NULL COMMENT '配置信息',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `callable_by_others` TINYINT(1) DEFAULT 1 COMMENT '是否可被其他Agent调用',
    `sub_agent_ids` JSON DEFAULT NULL COMMENT '子智能体ID列表(Agent as Tool模式)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent定义表';

-- 插入示例Agent
INSERT INTO `agent` (`id`, `name`, `description`, `system_prompt`, `model_name`, `enabled`, `callable_by_others`) VALUES
('default-assistant', '通用助手', '一个通用的AI助手', '你是一个有帮助的AI助手，请用中文回答用户的问题。', 'qwen-max', 1, 1);

-- Paper 业务智能体（Agent as Tool 模式）
-- 子智能体
INSERT INTO `agent` (`id`, `name`, `description`, `system_prompt`, `model_name`, `enabled`, `callable_by_others`) VALUES
('paper-reviewer', '审稿人', '负责审核论文内容质量', '你是一位资深学术审稿专家，负责审核论文的学术质量、逻辑性和创新性。', 'qwen-max', 1, 1),
('paper-writer', '写手', '负责撰写论文内容', '你是一位专业的学术写作专家，负责撰写高质量的论文内容。', 'qwen-max', 1, 1),
('paper-diagram', '画图Agent', '负责生成图表和可视化', '你是一位数据可视化专家，负责根据需求生成图表、流程图等可视化内容。', 'qwen-max', 1, 1),
('paper-experiment', '实验Agent', '负责设计和分析实验', '你是一位实验设计专家，负责设计实验方案并分析实验结果。', 'qwen-max', 1, 1);

-- 主智能体（老大），配置子智能体列表
INSERT INTO `agent` (`id`, `name`, `description`, `system_prompt`, `model_name`, `enabled`, `callable_by_others`, `sub_agent_ids`) VALUES
('paper-leader', '老大', '论文项目负责人，负责整体规划和任务分配', 
'你是论文项目的负责人，负责整体规划、任务分配和结果汇总。你可以调用以下专家来完成任务：
- call_paper_reviewer: 调用审稿人审核论文质量
- call_paper_writer: 调用写手撰写论文内容  
- call_paper_diagram: 调用画图Agent生成图表
- call_paper_experiment: 调用实验Agent设计和分析实验

根据用户需求，合理分配任务给各专家，并汇总他们的输出。', 
'qwen-max', 1, 0, '["paper-reviewer", "paper-writer", "paper-diagram", "paper-experiment"]');
