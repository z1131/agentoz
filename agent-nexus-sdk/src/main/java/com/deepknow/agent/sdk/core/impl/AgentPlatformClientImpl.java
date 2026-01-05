package com.deepknow.agent.sdk.core.impl;

import com.deepknow.agent.api.AgentService;
import com.deepknow.agent.api.SessionService;
import com.deepknow.agent.api.dto.CreateSessionRequest;
import com.deepknow.agent.api.dto.SessionDTO;
import com.deepknow.agent.sdk.AgentPlatformClient;
import com.deepknow.agent.sdk.agents.SessionHandle;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * 智能体平台 SDK 顶级客户端实现
 */
@Component
public class AgentPlatformClientImpl implements AgentPlatformClient {

    @DubboReference(version = "1.0.0", check = false)
    private AgentService agentService;

    @DubboReference(version = "1.0.0", check = false)
    private SessionService sessionService;

    @Override
    public SessionHandle openSession(String userId, String title) {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setUserId(userId);
        request.setTitle(title);

        SessionDTO session = sessionService.createSession(request);
        
        return new SessionHandleImpl(session.getSessionId(), agentService);
    }

    @Override
    public SessionHandle attachSession(String sessionId) {
        return new SessionHandleImpl(sessionId, agentService);
    }
}