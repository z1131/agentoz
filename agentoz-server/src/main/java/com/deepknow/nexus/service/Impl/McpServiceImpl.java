package com.deepknow.nexus.service.impl;

import com.deepknow.nexus.service.McpService;
import com.deepknow.nexus.model.AgentEntity;
import com.deepknow.agent.api.AgentService;
import com.deepknow.agent.api.dto.AgentRequest;
import com.deepknow.agent.api.dto.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpServiceImpl implements McpService {
    private static final Logger log = LoggerFactory.getLogger(McpServiceImpl.class);

    @Autowired private AgentService agentService;

    @Override
    public Map<String, Object> listTools() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "call_agent");
        tool.put("description", "Invoke another agent in current session.");
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", Map.of(
            "target_name", Map.of("type", "string"), 
            "message", Map.of("type", "string")
        ));
        inputSchema.put("required", List.of("target_name", "message"));
        tool.put("inputSchema", inputSchema);
        return Map.of("tools", List.of(tool));
    }

    @Override
    public Map<String, Object> callTool(String sessionId, Map<String, Object> params) {
        String name = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");
        if (!"call_agent".equals(name)) throw new RuntimeException("Unknown tool");

        String targetName = (String) args.get("target_name");
        String message = (String) args.get("message");
        log.info("MCP call_agent: target={}, session={}", targetName, sessionId);

        // 通过 agentService 寻找目标 ID
        String targetAgentId = agentService.getAgentIdByName(sessionId, targetName);
        if (targetAgentId == null) throw new RuntimeException("Target agent not found");

        // 直接发起同步 RPC 调用
        AgentRequest request = new AgentRequest();
        request.setSessionId(sessionId);
        request.setAgentId(targetAgentId);
        request.setQuery(message);
        
        AgentResponse response = agentService.execute(request);
        String result = (response != null && response.getSuccess()) ? response.getOutput() : "Error invoking agent";

        return Map.of("content", List.of(Map.of("type", "text", "text", result)));
    }
}