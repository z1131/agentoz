package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.Map;

public class AgentRequest implements Serializable {
    private String sessionId;
    private String agentId;
    private String query;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> extra;

    public AgentRequest() {}

    public AgentRequest(String sessionId, String agentId, String query, Double temperature, Integer maxTokens, Map<String, Object> extra) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.query = query;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.extra = extra;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String agentId;
        private String query;
        private Double temperature;
        private Integer maxTokens;
        private Map<String, Object> extra;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        public AgentRequest build() {
            return new AgentRequest(sessionId, agentId, query, temperature, maxTokens, extra);
        }
    }
}