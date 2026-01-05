package com.deepknow.nexus.service.impl;

import com.deepknow.agent.api.AgentService;
import com.deepknow.agent.api.dto.*;
import com.deepknow.nexus.common.DomainException;
import com.deepknow.nexus.model.AgentEntity;
import com.deepknow.nexus.infra.repo.AgentRepository;
import com.deepknow.nexus.infra.repo.SessionRepository;
import com.deepknow.nexus.infra.client.CodexAgentGrpcServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@DubboService(version = "1.0.0")
public class AgentServiceImpl implements AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    @Autowired private AgentRepository agentRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CodexAgentGrpcServiceImpl codexAgentClient;

    @Override
    @Transactional
    public AgentResponse execute(AgentRequest request) {
        log.info("执行 Agent 任务: sessionId={}, query={}", request.getSessionId(), request.getQuery());
        
        try {
            final String targetAgentId;
            if (request.getAgentId() != null && !request.getAgentId().isEmpty()) {
                targetAgentId = request.getAgentId();
            } else {
                targetAgentId = sessionRepository.getById(request.getSessionId())
                    .orElseThrow(() -> DomainException.sessionNotFound(request.getSessionId()))
                    .getPrimaryAgentId();
            }

            if (targetAgentId == null) throw new RuntimeException("No primary agent found");

            AgentEntity agent = agentRepository.getById(targetAgentId)
                .orElseThrow(() -> DomainException.agentNotFound(targetAgentId));

            agent.appendUserMessage(request.getQuery());
            agent.transitToThinking();
            agentRepository.save(agent);

            String output = codexAgentClient.infer(
                agent.getSessionId(),
                agent.getAgentId(),
                agent.getContext(),
                agent.getSystemPrompt(),
                agent.getMcpConfig(),
                request.getQuery()
            );

            agent.appendAssistantMessage(output);
            agent.transitToIdle();
            agentRepository.save(agent);

            AgentResponse res = new AgentResponse();
            res.setSuccess(true);
            res.setOutput(output);
            return res;
        } catch (Exception e) {
            log.error("Agent execution failed", e);
            AgentResponse res = new AgentResponse();
            res.setSuccess(false);
            res.setErrorMessage(e.getMessage());
            return res;
        }
    }

    @Override
    public Flux<AgentChunk> executeStream(AgentRequest request) {
        final String targetAgentId;
        if (request.getAgentId() != null && !request.getAgentId().isEmpty()) {
            targetAgentId = request.getAgentId();
        } else {
            targetAgentId = sessionRepository.getById(request.getSessionId())
                .orElseThrow(() -> DomainException.sessionNotFound(request.getSessionId()))
                .getPrimaryAgentId();
        }
        
        if (targetAgentId == null) return Flux.error(new RuntimeException("No primary agent found"));

        AgentEntity agent = agentRepository.getById(targetAgentId)
            .orElseThrow(() -> DomainException.agentNotFound(targetAgentId));

        return codexAgentClient.inferStream(
            agent.getSessionId(),
            agent.getAgentId(),
            agent.getContext(),
            agent.getSystemPrompt(),
            agent.getMcpConfig(),
            request.getQuery()
        );
    }

    @Override
    public String createAgent(CreateAgentRequest request) {
        AgentEntity agent = new AgentEntity();
        agent.setAgentId(java.util.UUID.randomUUID().toString());
        agent.setSessionId(request.getSessionId());
        agent.setAgentName(request.getAgentName());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setAgentType("SUB");
        agent.setState("IDLE");
        agent.setContext("[]");
        if (request.getMcpConfig() != null) agent.setMcpConfigMap(request.getMcpConfig());
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        agent.setLastUsedAt(now);

        agentRepository.save(agent);
        return agent.getAgentId();
    }

    @Override public String getAgentIdByName(String sessionId, String name) { return agentRepository.getBySessionAndName(sessionId, name).map(AgentEntity::getAgentId).orElse(null); }
    @Override public SessionDTO createChildSession(String p, String role, String t) { throw new UnsupportedOperationException(); }
    @Override public MessageListResponse getHistory(String sid) { return new MessageListResponse(); }
    @Override public void closeSession(String sid) { 
        sessionRepository.updateStatus(sid, "CLOSED");
    }
}