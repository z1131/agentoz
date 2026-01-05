package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.List;

public class CreateContextRequest implements Serializable {
    private String sessionId;
    private List<ContextData> contextData;

    public CreateContextRequest() {}

    public CreateContextRequest(String sessionId, List<ContextData> contextData) {
        this.sessionId = sessionId;
        this.contextData = contextData;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public List<ContextData> getContextData() { return contextData; }
    public void setContextData(List<ContextData> contextData) { this.contextData = contextData; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private List<ContextData> contextData;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder contextData(List<ContextData> contextData) {
            this.contextData = contextData;
            return this;
        }

        public CreateContextRequest build() {
            return new CreateContextRequest(sessionId, contextData);
        }
    }
}
