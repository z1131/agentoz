package com.deepknow.nexus.infra.repo;

import com.deepknow.nexus.model.AgentEntity;
import java.util.Optional;
import java.util.List;

public interface AgentRepository {
    void save(AgentEntity agent);
    Optional<AgentEntity> getById(String agentId);
    Optional<AgentEntity> getBySessionAndName(String sessionId, String agentName);
    List<AgentEntity> findBySessionId(String sessionId);
    void updateState(String agentId, String state);
    void updateTokenUsage(String agentId, int promptTokens, int completion_tokens);
    void deleteBySessionId(String sessionId);
    void deleteById(String agentId);
    
    // 快捷获取单例
    static AgentRepository getInstance() {
        return com.deepknow.nexus.common.SpringContextUtils.getBean(AgentRepository.class);
    }
}