package com.deepknow.agent.api.dto;
import java.io.Serializable;
import java.time.LocalDateTime;
public class SessionDTO implements Serializable {
    private String sessionId;
    private String userId;
    private String title;
    private String primaryAgentId;
    private String status;
    private LocalDateTime createdAt;
    public SessionDTO() {}
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPrimaryAgentId() { return primaryAgentId; }
    public void setPrimaryAgentId(String primaryAgentId) { this.primaryAgentId = primaryAgentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}