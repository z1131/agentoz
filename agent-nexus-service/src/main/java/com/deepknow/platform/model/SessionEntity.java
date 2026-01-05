package com.deepknow.platform.model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.time.LocalDateTime;

/**
 * 会话实体
 *
 * 业务语义：
 * - 用户与智能体系统的一次完整对话
 * - 管理参与会话的 Agent 列表
 * - 不关心具体 Agent 的配置（那是 AgentEntity 的事）
 */
@TableName("sessions")
public class SessionEntity {
    private static final Logger log = LoggerFactory.getLogger(SessionEntity.class);


    // ==================== 主键 ====================
    @TableId(value = "id")
    private Long id;

    /**
     * 会话ID（业务主键）
     */
    @TableField("session_id")
    private String sessionId;

    // ==================== 用户信息 ====================
    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    // ==================== 会话基本信息 ====================
    /**
     * 会话标题
     */
    @TableField("title")
    private String title;

    /**
     * 主 Agent 的 agentId
     *
     * 说明：
     * - 标识这个会话的主 Agent（兜底 Agent）
     * - 主 Agent 负责任务分发、结果整合
     * - 在会话创建时确定
     */
    @TableField("primary_agent_id")
    private String primaryAgentId;

    /**
     * 会话状态
     * 枚举：ACTIVE, INACTIVE, CLOSED
     */
    @TableField("status")
    private String status;

    // ==================== 统计信息 ====================
    /**
     * 总消息数（所有 Agent 产生的）
     */
    @TableField("total_message_count")
    private Integer totalMessageCount;

    /**
     * 总 Token 消耗（所有 Agent 累计）
     */
    @TableField("total_tokens_used")
    private Long totalTokensUsed;

    // ==================== 时间 ====================
    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("last_activity_at")
    private LocalDateTime lastActivityAt;

    // ==================== 辅助方法 ====================

    /**
     * 是否活跃
     */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    /**
     * 标记为活跃（更新活跃时间）
     */
    public void markActive() {
        this.status = "ACTIVE";
        this.lastActivityAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记为关闭
     */
    public void markClosed() {
        this.status = "CLOSED";
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Getter and Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPrimaryAgentId() {
        return primaryAgentId;
    }

    public void setPrimaryAgentId(String primaryAgentId) {
        this.primaryAgentId = primaryAgentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Integer getTotalMessageCount() {
        return totalMessageCount;
    }

    public void setTotalMessageCount(Integer totalMessageCount) {
        this.totalMessageCount = totalMessageCount;
    }

    public Long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public void setTotalTokensUsed(Long totalTokensUsed) {
        this.totalTokensUsed = totalTokensUsed;
    }
}
