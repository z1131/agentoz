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
}