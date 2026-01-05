package com.deepknow.nexus.infra.repo;

import com.deepknow.nexus.model.SessionEntity;
import java.util.Optional;
import java.util.List;

public interface SessionRepository {
    void save(SessionEntity session);
    Optional<SessionEntity> getById(String sessionId);
    List<SessionEntity> findByUserId(String userId);
    void updateStatus(String sessionId, String status);
    void updateActivity(String sessionId);
    void deleteById(String sessionId);
}
