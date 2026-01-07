package com.deepknow.agentoz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.AgentDefineRequest;
import com.deepknow.agentoz.api.dto.SessionCreateRequest;
import com.deepknow.agentoz.api.service.AgentManagerService;
import com.deepknow.agentoz.dto.codex.McpServerConfig;
import com.deepknow.agentoz.dto.codex.ModelProviderInfo;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.SessionEntity;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@DubboService
public class AgentManagerServiceImpl implements AgentManagerService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Override
    @Transactional
    public String createSession(SessionCreateRequest request) {
        String sessionId = UUID.randomUUID().toString();
        log.info("创建新会话: sessionId={}, userId={}", sessionId, request.getUserId());

        SessionEntity session = SessionEntity.builder()
                .sessionId(sessionId)
                .userId(request.getUserId())
                .businessCode(request.getBusinessCode())
                .title(request.getTitle())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .build();

        sessionRepository.insert(session);
        return sessionId;
    }

    @Override
    @Transactional
    public String defineAgent(AgentDefineRequest request) {
        String agentId = UUID.randomUUID().toString();
        log.info("装配 Agent: agentId={}, sessionId={}, type={}", 
                agentId, request.getSessionId(), request.getAgentType());

        ModelProviderInfo providerInfo = ModelProviderInfo.builder()
                .type(request.getConfig().getProvider().getType())
                .experimentalBearerToken(request.getConfig().getProvider().getApiKey())
                .baseUrl(request.getConfig().getProvider().getBaseUrl())
                .build();

        Map<String, McpServerConfig> mcpMap = new HashMap<>();
        if (request.getConfig().getMcpConfig() != null) {
            request.getConfig().getMcpConfig().forEach((name, conn) -> {
                mcpMap.put(name, McpServerConfig.builder()
                        .command("sse")
                        .args(java.util.List.of(conn.getUrl()))
                        .env(conn.getHeaders()) 
                        .build());
            });
        }

        AgentEntity agent = AgentEntity.builder()
                .agentId(agentId)
                .sessionId(request.getSessionId())
                .agentName(request.getAgentName())
                .agentType(request.getAgentType())
                .provider(providerInfo)
                .model(request.getConfig().getModel())
                .developerInstructions(request.getConfig().getDeveloperInstructions())
                .userInstructions(request.getConfig().getUserInstructions())
                .mcpConfig(mcpMap)
                .state("IDLE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        agentRepository.insert(agent);
        return agentId;
    }

    @Override
    public void removeAgent(String agentId) {
        agentRepository.delete(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId));
    }

    @Override
    public void endSession(String sessionId) {
        sessionRepository.delete(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionId, sessionId));
        agentRepository.delete(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getSessionId, sessionId));
    }
}
