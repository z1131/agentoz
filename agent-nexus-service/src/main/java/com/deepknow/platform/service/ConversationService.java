package com.deepknow.platform.service;

import com.deepknow.platform.common.JsonUtils;
import com.deepknow.platform.common.DomainException;
import com.deepknow.platform.model.AgentEntity;
import com.deepknow.platform.model.SessionEntity;
import com.deepknow.platform.repository.AgentRepository;
import com.deepknow.platform.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Transactional
    public String createSession(String userId, String title) {
        SessionEntity session = new SessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(title);
        session.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setLastActivityAt(now);
        sessionRepository.save(session);
        return session.getSessionId();
    }

    @Transactional
    public String createAgent(String sessionId, String agentName, String systemPrompt, Map<String, Object> mcpConfig) {
        sessionRepository.getById(sessionId).orElseThrow(() -> DomainException.sessionNotFound(sessionId));
        
        if (agentRepository.getBySessionAndName(sessionId, agentName).isPresent()) {
            throw new DomainException("AGENT_ALREADY_EXISTS", "Agent名称已存在: " + agentName);
        }

        AgentEntity agent = new AgentEntity();
        agent.setAgentId(UUID.randomUUID().toString());
        agent.setSessionId(sessionId);
        agent.setAgentName(agentName);
        agent.setSystemPrompt(systemPrompt);
        agent.setAgentType("SUB");
        agent.setState("IDLE");
        agent.setContext("[]");
        
        if (mcpConfig != null) {
            agent.setMcpConfigMap(mcpConfig);
        }

        LocalDateTime now = LocalDateTime.now();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        agent.setLastUsedAt(now);

        agentRepository.save(agent);
        return agent.getAgentId();
    }

    public Optional<AgentEntity> getAgentByName(String sessionId, String agentName) {
        return agentRepository.getBySessionAndName(sessionId, agentName);
    }

    public AgentEntity getAgent(String agentId) {
        return agentRepository.getById(agentId).orElseThrow(() -> DomainException.agentNotFound(agentId));
    }

    public SessionEntity getSession(String sessionId) {
        return sessionRepository.getById(sessionId).orElseThrow(() -> DomainException.sessionNotFound(sessionId));
    }

    @Transactional
    public void closeSession(String sessionId) {
        sessionRepository.updateStatus(sessionId, "CLOSED");
    }
}
