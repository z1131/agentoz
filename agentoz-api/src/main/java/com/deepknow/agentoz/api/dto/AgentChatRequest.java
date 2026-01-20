package com.deepknow.agentoz.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String agentId;

    private String message;

    private Map<String, Object> context;
}
