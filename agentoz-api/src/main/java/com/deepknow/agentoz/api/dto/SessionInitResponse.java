package com.deepknow.agentoz.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 会话初始化响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String primaryAgentId;

    private List<String> availableAgentIds;

    private boolean success;

    private String message;
}
