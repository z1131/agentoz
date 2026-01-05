package com.deepknow.nexus.model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * Agent 调用日志实体
 *
 * 业务语义：
 * - 记录 Agent 之间的调用关系
 * - 用于调试、审计、计费
 */

@TableName("agent_call_logs")
public class AgentCallLogEntity {
    private static final Logger log = LoggerFactory.getLogger(AgentCallLogEntity.class);


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 调用ID（业务主键）
     */
    @TableField("call_id")
    private String callId;

    /**
     * 所属会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 调用者 Agent ID
     */
    @TableField("from_agent_id")
    private String fromAgentId;

    /**
     * 被调用者 Agent ID（如果是调用 Agent）
     */
    @TableField("to_agent_id")
    private String toAgentId;

    /**
     * 调用类型
     * 枚举：AGENT_TO_AGENT, AGENT_TO_TOOL, TOOL_RESPONSE
     */
    @TableField("call_type")
    private String callType;

    /**
     * 被调用者的名称（Agent 名称或 Tool 名称）
     */
    @TableField("target_name")
    private String targetName;

    /**
     * 调用内容（用户消息）
     */
    @TableField("request_content")
    private String requestContent;

    /**
     * 响应内容
     */
    @TableField("response_content")
    private String responseContent;

    /**
     * Token 消耗
     */
    @TableField("tokens_used")
    private Integer tokensUsed;

    /**
     * 状态
     * 枚举：SUCCESS, ERROR, TIMEOUT
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    // ==================== 辅助方法 ====================

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(this.status);
    }

    /**
     * 获取耗时（毫秒）
     */
    public Long getDuration() {
        if (completedAt == null || startedAt == null) {
            return null;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /**
     * 标记开始
     */
    public void markStart() {
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 标记完成
     */
    public void markComplete(String status) {
        this.completedAt = LocalDateTime.now();
        this.status = status;
    }


    // ==================== Getter and Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFromAgentId() {
        return fromAgentId;
    }

    public void setFromAgentId(String fromAgentId) {
        this.fromAgentId = fromAgentId;
    }

    public String getToAgentId() {
        return toAgentId;
    }

    public void setToAgentId(String toAgentId) {
        this.toAgentId = toAgentId;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getRequestContent() {
        return requestContent;
    }

    public void setRequestContent(String requestContent) {
        this.requestContent = requestContent;
    }

    public String getResponseContent() {
        return responseContent;
    }

    public void setResponseContent(String responseContent) {
        this.responseContent = responseContent;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

}
