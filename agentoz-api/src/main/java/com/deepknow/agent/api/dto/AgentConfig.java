package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Agent 深度定制化配置
 */
public class AgentConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 指定使用的具体模型 (如 gpt-4o)
     */
    private String model;

    /**
     * 供应商信息 (包含 Key 等)
     */
    private ProviderConfig provider;

    /**
     * 开发者指令 (对应 Codex: developer_instructions)
     * 用于定义 Agent 的核心人设、业务逻辑和行为准则
     */
    private String developerInstructions;

    /**
     * 用户指令 (对应 Codex: user_instructions)
     * 用于定义用户的个性化偏好或任务特定补充指令
     */
    private String userInstructions;

    /**
     * 推理强度: low, medium, high
     */
    private String reasoningEffort;

    /**
     * MCP 工具服务器映射 (连接器模式)
     */
    private Map<String, McpConnectionConfig> mcpConfig;

    public AgentConfig() {}

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public ProviderConfig getProvider() { return provider; }
    public void setProvider(ProviderConfig provider) { this.provider = provider; }

    public String getDeveloperInstructions() { return developerInstructions; }
    public void setDeveloperInstructions(String developerInstructions) { this.developerInstructions = developerInstructions; }

    public String getUserInstructions() { return userInstructions; }
    public void setUserInstructions(String userInstructions) { this.userInstructions = userInstructions; }

    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }

    public Map<String, McpConnectionConfig> getMcpConfig() { return mcpConfig; }
    public void setMcpConfig(Map<String, McpConnectionConfig> mcpConfig) { this.mcpConfig = mcpConfig; }
}