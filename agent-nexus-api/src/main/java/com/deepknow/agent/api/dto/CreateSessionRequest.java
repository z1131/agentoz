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
public class CreateSessionRequest implements Serializable {
    private String userId;
    private String title;
    private String agentType;
    private String agentRole;
    private Map<String, Object> metadata;
}