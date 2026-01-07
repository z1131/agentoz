package com.deepknow.agentoz.api.dto;

import java.io.Serializable;

/**
 * 定义/装配 Agent 请求
 */
public class AgentDefineRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 所属会话 ID
     */
    private String sessionId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 智能体类型编码 (如 "coder", "analyst")
     */
    private String agentType;

    /**
     * 核心配置 (大脑与手脚)
     */
    private AgentConfig config;

    public AgentDefineRequest() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public AgentConfig getConfig() { return config; }
    public void setConfig(AgentConfig config) { this.config = config; }
}
