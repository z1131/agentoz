package com.deepknow.platform.rpc;

import com.deepknow.agent.api.SessionService;
import com.deepknow.agent.api.dto.*;
import com.deepknow.platform.model.SessionEntity;
import com.deepknow.platform.service.ConversationService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Session Dubbo 服务实现
 */
@DubboService(version = "1.0.0")
public class SessionRpcService implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionRpcService.class);

    @Autowired
    private ConversationService conversationService;

    @Override
    public SessionDTO createSession(CreateSessionRequest request) {
        String sessionId = conversationService.createSession(request.getUserId(), request.getTitle());
        SessionEntity session = conversationService.getSession(sessionId);
        
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setUserId(session.getUserId());
        dto.setTitle(session.getTitle());
        dto.setStatus(session.getStatus());
        dto.setCreatedAt(session.getCreatedAt());
        return dto;
    }

    @Override
    public SessionDTO getSession(String sessionId) {
        SessionEntity session = conversationService.getSession(sessionId);
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setUserId(session.getUserId());
        dto.setTitle(session.getTitle());
        dto.setStatus(session.getStatus());
        dto.setPrimaryAgentId(session.getPrimaryAgentId());
        return dto;
    }

    @Override
    public SessionDTO updateStatus(String sessionId, String status) {
        return getSession(sessionId);
    }

    @Override
    public MessageDTO addMessage(String sessionId, AddMessageRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MessageListResponse getMessages(String sessionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteSession(String sessionId) {
    }
}
