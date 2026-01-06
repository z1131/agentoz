package com.deepknow.agent.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 聊天请求对象 (手动实现 Getter/Setter 以保证极致兼容性)
 */
public class ChatRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private String sessionId;
    
    // 快捷字段 (最终会合并进 config)
    private String systemPrompt; 
    private List<String> tools; // 待废弃，建议直接配置 config.mcpServers

    // 核心配置 (新增)
    private AgentConfig config;

    private String message;
    private Map<String, Object> params;

    public ChatRequest() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public AgentConfig getConfig() { return config; }
    public void setConfig(AgentConfig config) { this.config = config; }
}
