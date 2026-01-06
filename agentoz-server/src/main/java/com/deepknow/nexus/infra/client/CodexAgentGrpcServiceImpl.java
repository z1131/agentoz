package com.deepknow.nexus.infra.client;

import com.deepknow.agent.api.model.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class CodexAgentGrpcServiceImpl {

    public String infer(String sessionId, String agentId, String context, String prompt, Object mcpConfig, String query) {
        // Mock
        return "Codex Mock Response";
    }

    public Flux<String> inferStream(String sessionId, String agentId, String context, String prompt, Object mcpConfig, String query) {
        // Mock
        return Flux.just("Codex", " ", "Mock", " ", "Stream");
    }
}
