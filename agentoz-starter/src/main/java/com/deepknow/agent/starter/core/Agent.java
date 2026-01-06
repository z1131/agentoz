package com.deepknow.agent.starter.core;

import com.deepknow.agent.api.model.AgentConfig;
import com.deepknow.agent.api.model.ChatRequest;
import com.deepknow.agent.api.model.ChatResponse;
import com.deepknow.agent.api.model.McpServerConfig;
import com.deepknow.agent.api.service.AgentService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 流式调用入口
 */
public class Agent {

    private String systemPrompt;
    private List<String> tools = new ArrayList<>();
    private String sessionId;
    private Map<String, Object> params = new HashMap<>();

    // 新增：MCP 配置存储
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();
    private String model;

    public Agent() {
        this.sessionId = UUID.randomUUID().toString();
    }

    public Agent prompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public Agent tool(String toolName) {
        this.tools.add(toolName);
        return this;
    }

    /**
     * 配置 MCP Server
     */
    public Agent mcpServer(String name, McpServerConfig config) {
        this.mcpServers.put(name, config);
        return this;
    }

    public Agent model(String model) {
        this.model = model;
        return this;
    }

    public Agent sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }
    
    public Agent param(String key, Object value) {
        this.params.put(key, value);
        return this;
    }

    public ChatResponse chat(String message) {
        AgentService service = AgentContext.getAgentService();
        
        ChatRequest request = new ChatRequest();
        request.setSessionId(this.sessionId);
        request.setSystemPrompt(this.systemPrompt);
        request.setMessage(message);
        request.setTools(this.tools);
        request.setParams(this.params);

        // 组装 Config
        AgentConfig config = new AgentConfig();
        config.setModel(this.model);
        config.setInstructions(this.systemPrompt); // 优先使用 systemPrompt
        config.setMcpServers(this.mcpServers);
        
        request.setConfig(config);
        
        return service.chat(request);
    }
}