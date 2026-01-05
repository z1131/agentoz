-- ===================================
-- MCP Tool 初始化数据
-- ===================================

-- 插入 WebSearch Tool
INSERT INTO `mcp_tool_definitions` (
  `tool_id`,
  `tool_name`,
  `tool_type`,
  `description`,
  `schema`,
  `third_party_url`,
  `auth_headers`,
  `timeout_seconds`,
  `is_active`,
  `created_at`,
  `updated_at`
) VALUES (
  'web_search_tool',
  'WebSearch',
  'THIRD_PARTY',
  '基于通义实验室 Text-Embedding，GTE-reRank，Query 改写，搜索判定等多种检索模型及语义理解，串接专业搜索工程框架及各类型实时信息检索工具，提供实时互联网全栈信息检索，提升 LLM 回答准确性及时效性。',
  '{"type": "object", "properties": {"query": {"type": "string", "description": "搜索关键词或问题"}, "maxResults": {"type": "integer", "description": "返回结果数量，默认 10", "default": 10}}, "required": ["query"]}',
  'https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/sse',
  '{"Authorization": "Bearer ${DASHSCOPE_API_KEY}"}',
  30,
  TRUE,
  NOW(),
  NOW()
);

-- 插入 call_agent Tool
INSERT INTO `mcp_tool_definitions` (
  `tool_id`,
  `tool_name`,
  `tool_type`,
  `description`,
  `schema`,
  `is_active`,
  `created_at`,
  `updated_at`
) VALUES (
  'call_agent_tool',
  'call_agent',
  'SELF_IMPLEMENTED',
  '调用其他 Agent 协作完成任务。当需要写作专家、代码专家、数据分析专家等其他 Agent 时使用此工具。',
  '{"type": "object", "properties": {"target_agent_name": {"type": "string", "description": "要调用的 Agent 名称"}, "message": {"type": "string", "description": "发送给目标 Agent 的任务描述或消息"}}, "required": ["target_agent_name", "message"]}',
  TRUE,
  NOW(),
  NOW()
);
