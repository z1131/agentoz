package com.deepknow.agent.infra.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agent.model.UserMessageEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisMessageRepository implements MessageRepository {

    @Autowired
    private UserMessageMapper userMessageMapper;

    @Override
    public void save(UserMessageEntity message) {
        userMessageMapper.insert(message);
    }

    @Override
    public List<UserMessageEntity> findBySessionId(String sessionId) {
        return userMessageMapper.selectList(
            new LambdaQueryWrapper<UserMessageEntity>().eq(UserMessageEntity::getSessionId, sessionId)
        );
    }

    @Override
    public List<UserMessageEntity> findByAgentId(String agentId) {
        return userMessageMapper.selectList(
            new LambdaQueryWrapper<UserMessageEntity>().eq(UserMessageEntity::getAgentId, agentId)
        );
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        userMessageMapper.delete(new LambdaQueryWrapper<UserMessageEntity>().eq(UserMessageEntity::getSessionId, sessionId));
    }

    @Override
    public int getNextSequence(String sessionId) {
        return (int) (userMessageMapper.selectCount(new LambdaQueryWrapper<UserMessageEntity>().eq(UserMessageEntity::getSessionId, sessionId)) + 1);
    }
}
