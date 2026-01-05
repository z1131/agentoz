package com.deepknow.platform.rpc;

import com.deepknow.agent.api.AgentService;
import com.deepknow.agent.api.dto.*;
import com.deepknow.platform.model.AgentEntity;
import com.deepknow.platform.service.AgentManager;
import com.deepknow.platform.service.ConversationService;
import com.deepknow.platform.client.CodexAgentGrpcServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

/**
 * Agent Dubbo 服务实现
 */
@DubboService(version = "1.0.0")
public class AgentRpcService implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentRpcService.class);

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentManager agentManager;

    @Autowired
    private CodexAgentGrpcServiceImpl codexAgentClient;

    @Override
    public AgentResponse execute(AgentRequest request) {
        log.info("RPC 调用: execute, sessionId={}, agentId={}, query={}", 
            request.getSessionId(), request.getAgentId(), request.getQuery());
        
        try {
            String targetAgentId = request.getAgentId();
            if (targetAgentId == null || targetAgentId.isEmpty()) {
                targetAgentId = conversationService.getSession(request.getSessionId())
                    .getPrimaryAgentId();
            }

            String output = agentManager.processTask(targetAgentId, request.getQuery());

            AgentResponse response = new AgentResponse();
            response.setSuccess(true);
            response.setOutput(output);
            return response;
        } catch (Exception e) {
            log.error("RPC 执行失败", e);
            AgentResponse response = new AgentResponse();
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
            return response;
        }
    }

    @Override
    public Flux<AgentChunk> executeStream(AgentRequest request) {
        String targetAgentId = request.getAgentId();
        if (targetAgentId == null || targetAgentId.isEmpty()) {
            targetAgentId = conversationService.getSession(request.getSessionId())
                .getPrimaryAgentId();
        }

        AgentEntity agent = conversationService.getAgent(targetAgentId);

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
        return conversationService.createAgent(
            request.getSessionId(),
            request.getAgentName(),
            request.getSystemPrompt(),
            request.getMcpConfig()
        );
    }

    @Override
    public String getAgentIdByName(String sessionId, String agentName) {
        return conversationService.getAgentByName(sessionId, agentName)
            .map(AgentEntity::getAgentId)
            .orElse(null);
    }

    @Override
    public SessionDTO createChildSession(String parentSessionId, String agentRole, String taskDescription) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MessageListResponse getHistory(String sessionId) {
        return new MessageListResponse();
    }

    @Override
    public void closeSession(String sessionId) {
        conversationService.closeSession(sessionId);
    }
}