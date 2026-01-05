package com.deepknow.nexus.model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 用户消息实体
 *
 * 业务语义：
 * - 用户可见的消息（用于前端展示）
 * - 不等同于 Agent.context（包含工具调用等内部细节）
 */

@TableName("user_messages")
public class UserMessageEntity {
    private static final Logger log = LoggerFactory.getLogger(UserMessageEntity.class);


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息ID（业务主键）
     */
    @TableField("message_id")
    private String messageId;

    /**
     * 所属会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 关联的 Agent ID
     * 说明：标识这条消息是哪个 Agent 产生的
     */
    @TableField("agent_id")
    private String agentId;

    /**
     * 角色
     * 枚举：user, assistant, system
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 是否对用户可见
     */
    @TableField("is_visible")
    private Boolean isVisible;

    /**
     * 序号（在会话中的顺序）
     */
    @TableField("sequence")
    private Integer sequence;

    /**
     * Token 数
     */
    @TableField("tokens")
    private Integer tokens;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    // ==================== 辅助方法 ====================

    /**
     * 是否为用户消息
     */
    public boolean isUserMessage() {
        return "user".equals(this.role);
    }

    /**
     * 是否为助手消息
     */
    public boolean isAssistantMessage() {
        return "assistant".equals(this.role);
    }


    // ==================== Getter and Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(Boolean isVisible) {
        this.isVisible = isVisible;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public Integer getTokens() {
        return tokens;
    }

    public void setTokens(Integer tokens) {
        this.tokens = tokens;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
