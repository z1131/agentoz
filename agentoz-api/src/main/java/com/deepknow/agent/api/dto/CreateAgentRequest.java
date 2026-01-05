package com.deepknow.agent.api.dto;
import java.io.Serializable;
import java.util.Map;
public class CreateAgentRequest implements Serializable {
    private String sessionId;
    private String agentName;
    private String systemPrompt;
    private Map<String, Object> mcpConfig;
    public CreateAgentRequest() {}
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Map<String, Object> getMcpConfig() { return mcpConfig; }
    public void setMcpConfig(Map<String, Object> mcpConfig) { this.mcpConfig = mcpConfig; }
}