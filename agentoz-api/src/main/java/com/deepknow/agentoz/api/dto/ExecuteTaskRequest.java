package com.deepknow.agentoz.api.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * 执行任务请求
 */
public class ExecuteTaskRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 指定执行任务的 Agent ID
     */
    private String agentId;

    /**
     * 用户输入的指令内容
     */
    private String message;

    /**
     * 运行时覆盖配置 (可选)
     * 允许针对单次调用微调参数，如 userInstructions
     */
    private Map<String, Object> overrides;

    public ExecuteTaskRequest() {}

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getOverrides() { return overrides; }
    public void setOverrides(Map<String, Object> overrides) { this.overrides = overrides; }
}
