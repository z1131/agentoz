package com.deepknow.nexus.service;

import java.util.Map;

/**
 * MCP 协议服务：负责处理 Agent 回调的协议对接
 */
public interface McpService {
    Map<String, Object> listTools();
    Map<String, Object> callTool(String sessionId, Map<String, Object> params);
}
