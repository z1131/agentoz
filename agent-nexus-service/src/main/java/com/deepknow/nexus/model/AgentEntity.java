package com.deepknow.nexus.model;

import com.baomidou.mybatisplus.annotation.*;
import com.deepknow.nexus.common.JsonUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TableName("agents")
public class AgentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentId;
    private String sessionId;
    private String agentName;
    private String agentType;
    private String systemPrompt;
    private String mcpConfig;
    private String context;
    private String state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;

    // 手写 Getter/Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getMcpConfig() { return mcpConfig; }
    public void setMcpConfig(String mcpConfig) { this.mcpConfig = mcpConfig; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    // ==================== 行为逻辑 ====================
    public void appendUserMessage(String text) {
        List<Map<String, Object>> history = parseContext();
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "message");
        msg.put("role", "user");
        msg.put("content", List.of(Map.of("type", "input_text", "text", text)));
        history.add(msg);
        this.context = JsonUtils.toJson(history);
    }

    public void appendAssistantMessage(String text) {
        List<Map<String, Object>> history = parseContext();
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "message");
        msg.put("role", "assistant");
        msg.put("content", List.of(Map.of("type", "output_text", "text", text)));
        history.add(msg);
        this.context = JsonUtils.toJson(history);
    }

    public void transitToThinking() {
        this.state = "THINKING";
        this.updatedAt = LocalDateTime.now();
    }

    public void transitToIdle() {
        this.state = "IDLE";
        this.lastUsedAt = LocalDateTime.now();
    }

    public Map<String, Object> getMcpConfigMap() {
        if (mcpConfig == null || mcpConfig.isEmpty()) return new HashMap<>();
        return JsonUtils.fromJson(mcpConfig, Map.class);
    }

    public void setMcpConfigMap(Map<String, Object> map) {
        this.mcpConfig = JsonUtils.toJson(map);
    }

    private List<Map<String, Object>> parseContext() {
        if (context == null || context.isEmpty() || "[]".equals(context)) return new ArrayList<>();
        return JsonUtils.fromJson(context, List.class);
    }
}
