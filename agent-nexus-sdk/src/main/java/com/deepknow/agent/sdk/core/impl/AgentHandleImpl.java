package com.deepknow.agent.sdk.core.impl;

import com.deepknow.agent.api.AgentService;
import com.deepknow.agent.api.dto.AgentRequest;
import com.deepknow.agent.api.dto.AgentResponse;
import com.deepknow.agent.api.dto.AgentChunk;
import com.deepknow.agent.sdk.agents.AgentHandle;
import reactor.core.publisher.Flux;

/**
 * 智能体句柄实现
 */
public class AgentHandleImpl implements AgentHandle {

    private final String sessionId;
    private final String agentId;
    private final String agentName;
    private final AgentService agentService;

    public AgentHandleImpl(String sessionId, String agentId, String agentName, AgentService agentService) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.agentService = agentService;
    }

    @Override
    public String getName() {
        return agentName;
    }

    @Override
    public String ask(String message) {
        AgentRequest request = new AgentRequest();
        request.setSessionId(sessionId);
        request.setAgentId(agentId);
        request.setQuery(message);

        AgentResponse response = agentService.execute(request);
        if (response == null || response.getSuccess() == null || !response.getSuccess()) {
            String error = response != null ? response.getErrorMessage() : "Unknown RPC error";
            throw new RuntimeException("Agent ask 失败: " + error);
        }

        return response.getOutput();
    }

    @Override
    public Flux<String> streamAsk(String message) {
        AgentRequest request = new AgentRequest();
        request.setSessionId(sessionId);
        request.setAgentId(agentId);
        request.setQuery(message);

        return agentService.executeStream(request)
            .filter(chunk -> "TEXT".equals(chunk.getType()))
            .map(AgentChunk::getContent);
    }

    @Override
    public String inferOnly(String message) {
        return ask(message);
    }
}