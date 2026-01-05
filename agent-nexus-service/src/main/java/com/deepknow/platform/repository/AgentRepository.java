package com.deepknow.platform.repository;

import com.deepknow.platform.model.AgentEntity;
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
        return com.deepknow.platform.common.SpringContextUtils.getBean(AgentRepository.class);
    }
}