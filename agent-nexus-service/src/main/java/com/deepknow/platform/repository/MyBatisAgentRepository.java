package com.deepknow.platform.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.platform.model.AgentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAgentRepository implements AgentRepository {

    @Autowired
    private AgentMapper agentMapper;

    @Override
    public void save(AgentEntity agent) {
        if (agent.getId() == null) {
            agentMapper.insert(agent);
        } else {
            agentMapper.updateById(agent);
        }
    }

    @Override
    public Optional<AgentEntity> getById(String agentId) {
        return Optional.ofNullable(agentMapper.selectOne(
            new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
        ));
    }

    @Override
    public Optional<AgentEntity> getBySessionAndName(String sessionId, String agentName) {
        return Optional.ofNullable(agentMapper.selectOne(
            new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getSessionId, sessionId)
                .eq(AgentEntity::getAgentName, agentName)
        ));
    }

    @Override
    public List<AgentEntity> findBySessionId(String sessionId) {
        return agentMapper.selectList(
            new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getSessionId, sessionId)
        );
    }

    @Override
    public void updateState(String agentId, String state) {
        AgentEntity agent = new AgentEntity();
        agent.setState(state);
        agentMapper.update(agent, new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId));
    }

    @Override
    public void updateTokenUsage(String agentId, int promptTokens, int completion_tokens) {
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        agentMapper.delete(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getSessionId, sessionId));
    }

    @Override
    public void deleteById(String agentId) {
        agentMapper.delete(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId));
    }
}
