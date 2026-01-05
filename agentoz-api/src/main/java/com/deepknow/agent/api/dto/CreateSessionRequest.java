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

    public CreateSessionRequest(String userId, String title, String agentType, String agentRole, Map<String, Object> metadata) {
        this.userId = userId;
        this.title = title;
        this.agentType = agentType;
        this.agentRole = agentRole;
        this.metadata = metadata;
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String title;
        private String agentType;
        private String agentRole;
        private Map<String, Object> metadata;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder agentRole(String agentRole) {
            this.agentRole = agentRole;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public CreateSessionRequest build() {
            return new CreateSessionRequest(userId, title, agentType, agentRole, metadata);
        }
    }
}