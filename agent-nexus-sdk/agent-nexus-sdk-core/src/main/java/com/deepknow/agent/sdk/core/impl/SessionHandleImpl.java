package com.deepknow.agent.sdk.core.impl;

import com.deepknow.agent.api.AgentService;
import com.deepknow.agent.api.dto.CreateAgentRequest;
import com.deepknow.agent.sdk.agents.AgentHandle;
import com.deepknow.agent.sdk.agents.SessionHandle;
import com.deepknow.agent.sdk.model.AgentDefinition;

import java.util.Map;
import java.util.HashMap;

/**
 * 会话句柄实现
 */
public class SessionHandleImpl implements SessionHandle {

    private final String sessionId;
    private final AgentService agentService;

    public SessionHandleImpl(String sessionId, AgentService agentService) {
        this.sessionId = sessionId;
        this.agentService = agentService;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public AgentHandle spawnAgent(AgentDefinition definition) {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setSessionId(sessionId);
        request.setAgentName(definition.getName());
        request.setSystemPrompt(definition.getSystemPrompt());
        
        Map<String, Object> mcpMap = new HashMap<>();
        if (definition.getMcpServers() != null) {
            definition.getMcpServers().forEach((k, v) -> mcpMap.put(k, v));
        }
        request.setMcpConfig(mcpMap);

        String agentId = agentService.createAgent(request);
        
        return new AgentHandleImpl(sessionId, agentId, definition.getName(), agentService);
    }

    @Override
    public AgentHandle getAgent(String agentName) {
        String agentId = agentService.getAgentIdByName(sessionId, agentName);
        if (agentId == null) {
            throw new RuntimeException("Agent 不存在: " + agentName);
        }
        return new AgentHandleImpl(sessionId, agentId, agentName, agentService);
    }

    @Override
    public void close() {
        agentService.closeSession(sessionId);
    }
}