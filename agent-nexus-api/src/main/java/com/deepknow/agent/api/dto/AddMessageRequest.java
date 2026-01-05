package com.deepknow.agent.api.dto;

import java.io.Serializable;

public class AddMessageRequest implements Serializable {
    private String sessionId;
    private String role;
    private String content;

    public AddMessageRequest() {}

    public AddMessageRequest(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String role;
        private String content;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public AddMessageRequest build() {
            return new AddMessageRequest(sessionId, role, content);
        }
    }
}
