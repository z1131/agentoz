package com.deepknow.nexus.infra.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.nexus.model.SessionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisSessionRepository implements SessionRepository {

    @Autowired
    private SessionMapper sessionMapper;

    @Override
    public void save(SessionEntity session) {
        if (session.getId() == null) {
            sessionMapper.insert(session);
        } else {
            sessionMapper.updateById(session);
        }
    }

    @Override
    public Optional<SessionEntity> getById(String sessionId) {
        return Optional.ofNullable(sessionMapper.selectOne(
            new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionId, sessionId)
        ));
    }

    @Override
    public List<SessionEntity> findByUserId(String userId) {
        return sessionMapper.selectList(
            new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getUserId, userId)
        );
    }

    @Override
    public void updateStatus(String sessionId, String status) {
        SessionEntity session = new SessionEntity();
        session.setStatus(status);
        sessionMapper.update(session, new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionId, sessionId));
    }

    @Override
    public void updateActivity(String sessionId) {
    }

    @Override
    public void deleteById(String sessionId) {
        sessionMapper.delete(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionId, sessionId));
    }
}
