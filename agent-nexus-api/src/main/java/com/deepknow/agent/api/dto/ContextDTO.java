package com.deepknow.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 上下文DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String contextId;

    private String sessionId;

    private String tenantId;

    private Map<String, Object> data;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
