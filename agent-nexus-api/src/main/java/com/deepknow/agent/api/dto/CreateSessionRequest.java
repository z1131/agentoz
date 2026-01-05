package com.deepknow.agent.api.dto;
import java.io.Serializable;
import java.util.Map;
public class CreateSessionRequest implements Serializable {
    private String userId;
    private String title;
    private String agentType;
    private String agentRole;
    private Map<String, Object> metadata;
    public CreateSessionRequest() {}
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getAgentRole() { return agentRole; }
    public void setAgentRole(String agentRole) { this.agentRole = agentRole; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}