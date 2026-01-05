package com.deepknow.nexus.infra.repo;

import com.deepknow.nexus.model.AgentCallLogEntity;
import java.util.List;

public interface AgentCallLogRepository {
    void save(AgentCallLogEntity log);
    List<AgentCallLogEntity> findBySessionId(String sessionId);
    List<AgentCallLogEntity> findByFromAgentId(String agentId);
    void updateStatus(String callId, String status, String responseContent, String errorMessage);
}
