package com.deepknow.agent.api.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置
 * 对应 Codex 的 McpServerConfig (Rust)
 */
public class McpServerConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    // ===== 基础配置 =====
    private boolean enabled = true;
    private List<String> enabledTools;
    private List<String> disabledTools;

    // ===== Stdio Transport (本地进程) =====
    private String command;
    private List<String> args;
    private Map<String, String> env;

    // ===== HTTP Transport (远程服务/SSE) =====
    private String url;
    
    // 构造函数
    public McpServerConfig() {}

    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getEnabledTools() { return enabledTools; }
    public void setEnabledTools(List<String> enabledTools) { this.enabledTools = enabledTools; }

    public List<String> getDisabledTools() { return disabledTools; }
    public void setDisabledTools(List<String> disabledTools) { this.disabledTools = disabledTools; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args; }

    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
