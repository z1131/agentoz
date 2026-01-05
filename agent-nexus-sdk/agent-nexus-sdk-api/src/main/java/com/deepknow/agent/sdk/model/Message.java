package com.deepknow.agent.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息模型
 *
 * @author Agent Platform
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 角色: user, assistant, system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 序号
     */
    private Integer sequence;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
