package com.deepknow.platform.model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.deepknow.platform.common.JsonUtils;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 实体
 *
 * 业务语义：
 * - 一个 Session 中参与对话的 Agent
 * - 每个 Agent 维护自己的配置和上下文
 * - 通过 agent_id 全局唯一标识
 *
 * 核心概念：
 * - Agent = 配置 + Context
 * - 一个 Agent 对应一个独立的上下文
 */
@TableName("agents")
public class AgentEntity {
    private static final Logger log = LoggerFactory.getLogger(AgentEntity.class);


    // ==================== 主键 ====================
    @TableId(value = "id")
    private Long id;

    /**
     * Agent ID（业务主键）
     */
    @TableField("agent_id")
    private String agentId;

    // ==================== 关联关系 ====================
    /**
     * 所属会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * Agent 类型
     *
     * 说明：
     * - PRIMARY: 主 Agent（会话创建时的默认 Agent）
     * - SUB: 子 Agent（通过 call_agent 调用的 Agent）
     */
    @TableField("agent_type")
    private String agentType;

    // ==================== Agent 配置 ====================
    /**
     * Agent 名称（显示名称，自然语言描述）
     *
     * 说明：
     * - 用于 AI 和人类识别
     * - 全局唯一，不允许重复（数据库唯一索引）
     * - 作为 Agent 的业务标识符
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * 系统提示词
     */
    @TableField("system_prompt")
    private String systemPrompt;

    /**
     * MCP服务器配置（JSON格式）
     *
     * 示例：
     * {
     *   "web_search": { "url": "...", "type": "streamable_http" }
     * }
     */
    @TableField("mcp_config")
    private String mcpConfig;

    /**
     * Agent 特定配置（JSON 格式）
     *
     * 示例：
     * {
     *   "temperature": 0.7,
     *   "maxTokens": 4000,
     *   "language": "Java"
     * }
     */
    @TableField("config")
    private String config;

    // ==================== Context（上下文） ====================
    /**
     * 历史记录（JSON 格式）
     *
     * 说明：
     * - codex-agent 格式的 ResponseItem[] 序列化后的 JSON
     * - 这是 Agent 的"记忆"
     */
    @TableField("context")
    private String context;

    /**
     * Prompt tokens（最后一次调用）
     */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /**
     * Completion tokens（最后一次调用）
     */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /**
     * 总 tokens（累计）
     */
    @TableField("total_tokens")
    private Integer totalTokens;

    /**
     * 最大 tokens（上下文窗口大小）
     */
    @TableField("max_tokens")
    private Integer maxTokens;

    /**
     * 估算的当前 token 数
     */
    @TableField("token_count_estimated")
    private Integer tokenCountEstimated;

    // ==================== 状态 ====================
    /**
     * Agent 状态
     *
     * 说明：
     * - IDLE: 空闲，可以接受新任务
     * - THINKING: 推理中
     * - CALLING: 正在调用其他 Agent 或 Tool
     * - ERROR: 错误状态
     */
    @TableField("state")
    private String state;

    /**
     * 版本号（乐观锁）
     */
    @TableField("version")
    private Integer version;

    // ==================== 时间 ====================
    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    // ==================== 辅助方法 ====================

    /**
     * 是否为主 Agent
     */
    public boolean isPrimaryAgent() {
        return "PRIMARY".equals(this.agentType);
    }

    /**
     * 是否为子 Agent
     */
    public boolean isSubAgent() {
        return "SUB".equals(this.agentType);
    }

    /**
     * 是否空闲
     */
    public boolean isIdle() {
        return "IDLE".equals(this.state);
    }

    /**
     * 标记为推理中
     */
    public void markThinking() {
        this.state = "THINKING";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为空闲
     */
    public void markIdle() {
        this.state = "IDLE";
        this.updatedAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * 添加历史记录项
     */
    public void appendContext(String newItemsJson) {
        if (this.context == null || this.context.isEmpty() || "[]".equals(this.context)) {
            this.context = newItemsJson;
        } else {
            // 合并 JSON 数组（简化处理）
            this.context = this.context.substring(0, this.context.length() - 1) +
                "," + newItemsJson.substring(1);
        }
        this.updatedAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
        this.version++;
    }

    /**
     * 替换历史记录
     */
    public void replaceContext(String itemsJson) {
        this.context = itemsJson;
        this.updatedAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
        this.version++;
    }

    /**
     * 是否需要截断上下文
     */
    public boolean needsContextTruncation() {
        if (maxTokens == null || tokenCountEstimated == null) {
            return false;
        }
        return tokenCountEstimated > (maxTokens * 0.8);
    }

    /**
     * 获取 MCP 配置 Map
     */
    public Map<String, Object> getMcpConfigMap() {
        if (mcpConfig == null || mcpConfig.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return JsonUtils.fromJson(mcpConfig, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 设置 MCP 配置
     */
    public void setMcpConfigMap(Map<String, Object> mcpConfigMap) {
        this.mcpConfig = JsonUtils.toJson(mcpConfigMap);
    }

    /**
     * 获取配置对象
     */
    public Map<String, Object> getConfigMap() {
        if (config == null || config.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return JsonUtils.fromJson(config, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 获取配置项
     */
    public Object getConfigValue(String key) {
        return getConfigMap().get(key);
    }

    /**
     * 设置配置项
     */
    public void setConfigValue(String key, Object value) {
        Map<String, Object> configMap = new HashMap<>(getConfigMap());
        configMap.put(key, value);
        this.config = JsonUtils.toJson(configMap);
    }

    // ==================== Getter and Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getMcpConfig() {
        return mcpConfig;
    }

    public void setMcpConfig(String mcpConfig) {
        this.mcpConfig = mcpConfig;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getTokenCountEstimated() {
        return tokenCountEstimated;
    }

    public void setTokenCountEstimated(Integer tokenCountEstimated) {
        this.tokenCountEstimated = tokenCountEstimated;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
