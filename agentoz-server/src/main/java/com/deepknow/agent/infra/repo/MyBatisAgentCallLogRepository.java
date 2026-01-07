package com.deepknow.agent.infra.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agent.model.AgentCallLogEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisAgentCallLogRepository implements AgentCallLogRepository {

    @Autowired
    private AgentCallLogMapper agentCallLogMapper;

    @Override
    public void save(AgentCallLogEntity log) {
        agentCallLogMapper.insert(log);
    }

    @Override
    public List<AgentCallLogEntity> findBySessionId(String sessionId) {
        return agentCallLogMapper.selectList(
            new LambdaQueryWrapper<AgentCallLogEntity>().eq(AgentCallLogEntity::getSessionId, sessionId)
        );
    }

    @Override
    public List<AgentCallLogEntity> findByFromAgentId(String agentId) {
        return agentCallLogMapper.selectList(
            new LambdaQueryWrapper<AgentCallLogEntity>().eq(AgentCallLogEntity::getFromAgentId, agentId)
        );
    }

    @Override
    public void updateStatus(String callId, String status, String responseContent, String errorMessage) {
        AgentCallLogEntity log = new AgentCallLogEntity();
        log.setStatus(status);
        log.setResponseContent(responseContent);
        log.setErrorMessage(errorMessage);
        agentCallLogMapper.update(log, new LambdaQueryWrapper<AgentCallLogEntity>().eq(AgentCallLogEntity::getCallId, callId));
    }
}