package com.deepknow.agent.sdk.model;

import com.deepknow.agent.sdk.model.mcp.McpServerConfig;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class AgentConfig implements Serializable {
    private String tenantId;
    private String userId;
    private String agentRole; // 对应 agentName
    private String agentType; // Added agentType
    private String title;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> context = new HashMap<>();
    private Map<String, McpServerConfig> mcpServers = new HashMap<>();
    private Map<String, Object> extra = new HashMap<>();

    public AgentConfig() {}

    public AgentConfig(String tenantId, String userId, String agentRole, String agentType, String title, String systemPrompt, Double temperature, Integer maxTokens, Map<String, Object> context, Map<String, McpServerConfig> mcpServers, Map<String, Object> extra) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.agentRole = agentRole;
        this.agentType = agentType;
        this.title = title;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.context = context;
        this.mcpServers = mcpServers;
        this.extra = extra;
    }

    public void addContext(String key, Object value) {
        this.context.put(key, value);
    }

    public void addMcpServer(String name, McpServerConfig config) {
        this.mcpServers.put(name, config);
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAgentRole() { return agentRole; }
    public void setAgentRole(String agentRole) { this.agentRole = agentRole; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public Map<String, McpServerConfig> getMcpServers() { return mcpServers; }
    public void setMcpServers(Map<String, McpServerConfig> mcpServers) { this.mcpServers = mcpServers; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String userId;
        private String agentRole;
        private String agentType;
        private String title;
        private String systemPrompt;
        private Double temperature;
        private Integer maxTokens;
        private Map<String, Object> context = new HashMap<>();
        private Map<String, McpServerConfig> mcpServers = new HashMap<>();
        private Map<String, Object> extra = new HashMap<>();

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder agentRole(String agentRole) {
            this.agentRole = agentRole;
            return this;
        }

        public Builder agentType(String agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
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

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder mcpServers(Map<String, McpServerConfig> mcpServers) {
            this.mcpServers = mcpServers;
            return this;
        }

        public Builder extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        public AgentConfig build() {
            return new AgentConfig(tenantId, userId, agentRole, agentType, title, systemPrompt, temperature, maxTokens, context, mcpServers, extra);
        }
    }
}
