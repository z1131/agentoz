package com.deepknow.agentoz.api.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * 执行任务请求
 */
public class ExecuteTaskRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 指定执行任务的 Agent ID (可选)
     */
    private String agentId;

    /**
     * 所属会话 ID (若 agentId 为空，则默认使用该会话的主智能体)
     */
    private String conversationId;

    /**
     * 用户输入的指令内容
     */
    private String message;

    /**
     * 运行时覆盖配置 (可选)
     */
    private Map<String, Object> overrides;

    /**
     * 消息发送者角色 (user / assistant)
     */
    private String role;

    /**
     * 发送者名称 (用于业务显示)
     */
    private String senderName;

    public ExecuteTaskRequest() {}

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getOverrides() { return overrides; }
    public void setOverrides(Map<String, Object> overrides) { this.overrides = overrides; }
}
