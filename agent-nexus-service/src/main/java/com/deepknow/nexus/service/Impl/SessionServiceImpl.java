package com.deepknow.nexus.service.impl;

import com.deepknow.agent.api.SessionService;
import com.deepknow.agent.api.dto.*;
import com.deepknow.nexus.model.SessionEntity;
import com.deepknow.nexus.infra.repo.SessionRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@DubboService(version = "1.0.0")
public class SessionServiceImpl implements SessionService {

    @Autowired private SessionRepository sessionRepository;

    @Override
    @Transactional
    public SessionDTO createSession(CreateSessionRequest request) {
        SessionEntity session = new SessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(request.getUserId());
        session.setTitle(request.getTitle());
        session.setStatus("ACTIVE");
        
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setLastActivityAt(now);

        sessionRepository.save(session);
        
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
        SessionEntity session = sessionRepository.getById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setUserId(session.getUserId());
        dto.setTitle(session.getTitle());
        dto.setStatus(session.getStatus());
        dto.setPrimaryAgentId(session.getPrimaryAgentId());
        return dto;
    }

    @Override public SessionDTO updateStatus(String sid, String s) { return getSession(sid); }
    @Override public MessageDTO addMessage(String sid, AddMessageRequest r) { throw new UnsupportedOperationException(); }
    @Override public MessageListResponse getMessages(String sid) { throw new UnsupportedOperationException(); }
    @Override public void deleteSession(String sid) { }
}
