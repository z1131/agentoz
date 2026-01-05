package com.deepknow.agent.sdk.model;

import com.deepknow.agent.sdk.model.mcp.McpServerConfig;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AgentDefinition implements Serializable {
    private String name;
    private String description;
    private String version;
    private String systemPrompt;
    private Double temperature;
    private List<McpServerConfig> mcpServers = new ArrayList<>();

    public AgentDefinition() {}

    public AgentDefinition(String name, String description, String version, String systemPrompt, Double temperature, List<McpServerConfig> mcpServers) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.mcpServers = mcpServers;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public List<McpServerConfig> getMcpServers() { return mcpServers; }
    public void setMcpServers(List<McpServerConfig> mcpServers) { this.mcpServers = mcpServers; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String version;
        private String systemPrompt;
        private Double temperature;
        private List<McpServerConfig> mcpServers = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
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

        public Builder mcpServers(List<McpServerConfig> mcpServers) {
            this.mcpServers = mcpServers;
            return this;
        }

        public AgentDefinition build() {
            return new AgentDefinition(name, description, version, systemPrompt, temperature, mcpServers);
        }
    }
}
