package com.deepknow.agent.api.model;

import java.io.Serializable;
import java.util.Map;

/**
 * Agent 运行配置
 * 对应 Codex 的 SessionConfigDto (Rust)
 */
public class AgentConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    // 模型相关
    private String model;
    private String provider; // e.g. "openai", "anthropic"

    // 提示词相关
    private String instructions; // System Prompt (developer_instructions)

    // 工具相关 (MCP Servers)
    // Key: Server名称, Value: 配置详情
    private Map<String, McpServerConfig> mcpServers;

    public AgentConfig() {}

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public Map<String, McpServerConfig> getMcpServers() { return mcpServers; }
    public void setMcpServers(Map<String, McpServerConfig> mcpServers) { this.mcpServers = mcpServers; }
}
