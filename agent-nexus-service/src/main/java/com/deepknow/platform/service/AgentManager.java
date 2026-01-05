package com.deepknow.platform.service;

import com.deepknow.platform.common.JsonUtils;
import com.deepknow.platform.model.AgentEntity;
import com.deepknow.platform.repository.AgentRepository;
import com.deepknow.platform.repository.MessageRepository;
import com.deepknow.platform.client.CodexAgentGrpcServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentManager {
    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private CodexAgentGrpcServiceImpl codexAgentClient;

    @Transactional
    public String processTask(String agentId, String userMessage) {
        AgentEntity agent = agentRepository.getById(agentId)
            .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        // 1. 追加用户消息
        appendUserMessage(agent, userMessage);
        
        // 2. 更新状态为 THINKING
        agentRepository.updateState(agentId, "THINKING");

        try {
            // 3. 调用推理
            String response = codexAgentClient.infer(
                agent.getSessionId(),
                agent.getAgentId(),
                agent.getContext(),
                agent.getSystemPrompt(),
                agent.getMcpConfig(),
                userMessage
            );

            // 4. 追加助手消息
            appendAssistantMessage(agent, response);
            
            // 5. 更新状态为 IDLE
            agentRepository.updateState(agentId, "IDLE");
            
            return response;
        } catch (Exception e) {
            agentRepository.updateState(agentId, "ERROR");
            throw e;
        }
    }

    private void appendUserMessage(AgentEntity agent, String text) {
        List<Map<String, Object>> context = parseContext(agent.getContext());
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "message");
        msg.put("role", "user");
        msg.put("content", List.of(Map.of("type", "input_text", "text", text)));
        context.add(msg);
        agent.setContext(JsonUtils.toJson(context));
        agentRepository.save(agent);
    }

    private void appendAssistantMessage(AgentEntity agent, String text) {
        List<Map<String, Object>> context = parseContext(agent.getContext());
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "message");
        msg.put("role", "assistant");
        msg.put("content", List.of(Map.of("type", "output_text", "text", text)));
        context.add(msg);
        agent.setContext(JsonUtils.toJson(context));
        agentRepository.save(agent);
    }

    private List<Map<String, Object>> parseContext(String contextJson) {
        if (contextJson == null || contextJson.isEmpty()) return new ArrayList<>();
        return JsonUtils.fromJson(contextJson, List.class);
    }
}