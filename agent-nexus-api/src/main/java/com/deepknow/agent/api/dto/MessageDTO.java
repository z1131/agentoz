package com.deepknow.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;

    private String sessionId;

    private String role; // user, assistant, system

    private String content;

    private Integer sequence;

    private LocalDateTime createdAt;
}
