package com.deepknow.agent.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 智能体配置
 *
 * @author Agent Platform
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 智能体类型: chat, writer, coder
     */
    @Builder.Default
    private String agentType = "chat";

    /**
     * 智能体角色名称
     */
    private String agentRole;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 温度参数 (0.0 - 1.0)
     */
    @Builder.Default
    private Double temperature = 0.7;

    /**
     * 最大tokens数
     */
    @Builder.Default
    private Integer maxTokens = 2000;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 额外元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 额外参数
     */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();

    /**
     * 添加上下文
     */
    public AgentConfig addContext(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 添加额外参数
     */
    public AgentConfig addExtra(String key, Object value) {
        if (this.extra == null) {
            this.extra = new HashMap<>();
        }
        this.extra.put(key, value);
        return this;
    }
}
