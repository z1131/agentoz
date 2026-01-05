package com.deepknow.agent.api.dto;

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
public class AgentRequest implements Serializable {
    private String sessionId;
    private String agentId;
    private String query;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> extra;
}