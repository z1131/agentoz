package com.deepknow.nexus.infra.repo;

import com.deepknow.nexus.model.UserMessageEntity;
import java.util.List;

public interface MessageRepository {
    void save(UserMessageEntity message);
    List<UserMessageEntity> findBySessionId(String sessionId);
    List<UserMessageEntity> findByAgentId(String agentId);
    void deleteBySessionId(String sessionId);
    int getNextSequence(String sessionId);
}
