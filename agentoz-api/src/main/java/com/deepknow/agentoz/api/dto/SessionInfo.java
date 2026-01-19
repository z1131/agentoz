package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话状态信息 DTO
 *
 * <p>用于断线重连时查询会话状态</p>
 *
 * @author DeepKnow
 * @since 2026-01-19
 */
@Data
public class SessionInfo implements Serializable {

    /**
     * 会话 ID (conversationId)
     */
    private String conversationId;

    /**
     * 会话状态
     */
    private String status;

    /**
     * 当前订阅者数量
     */
    private Integer subscriberCount;

    /**
     * 会话创建时间戳
     */
    private Long createdAt;

    /**
     * 最后更新时间戳
     */
    private Long updatedAt;

    /**
     * 主任务 ID
     */
    private String mainTaskId;

    /**
     * 当前 Agent ID
     */
    private String currentAgentId;

    /**
     * 活跃子任务数量
     */
    private Integer activeTaskCount;
}
