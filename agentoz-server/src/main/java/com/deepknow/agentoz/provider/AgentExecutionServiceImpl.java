package com.deepknow.agentoz.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.infra.converter.grpc.TaskResponseProtoConverter;
import com.deepknow.agentoz.infra.converter.grpc.HistoryProtoConverter;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.StreamGuard;
import com.deepknow.agentoz.infra.util.JwtUtils;
import codex.agent.HistoryItem;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行服务实现 (数据面)
 */
@Slf4j
@DubboService(protocol = "tri", timeout = 300000)
public class AgentExecutionServiceImpl implements AgentExecutionService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private CodexAgentClient codexAgentClient;
    
    @Autowired
    private JwtUtils jwtUtils;

    private final String websiteUrl = "https://agentoz.deepknow.com";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String traceInfo = "ConvId=" + request.getConversationId();
        
        StreamGuard.run(responseObserver, () -> {
            String agentId = request.getAgentId();
            String conversationId = request.getConversationId();

            log.info(">>> 收到任务请求: {}", traceInfo);

            if (agentId == null || agentId.isEmpty()) {
                if (conversationId == null || conversationId.isEmpty()) {
                    throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM, "agentId 和 conversationId 不能同时为空");
                }
                AgentEntity primaryAgent = agentRepository.selectOne(
                        new LambdaQueryWrapper<AgentEntity>()
                                .eq(AgentEntity::getConversationId, conversationId)
                                .eq(AgentEntity::getIsPrimary, true)
                );
                if (primaryAgent == null) {
                    throw new AgentOzException(AgentOzErrorCode.PRIMARY_AGENT_MISSING, conversationId);
                }
                agentId = primaryAgent.getAgentId();
                log.info("自动路由至主智能体: agentId={}", agentId);
            }

            final String finalAgentId = agentId;
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, finalAgentId)
            );

            if (agent == null) {
                throw new AgentOzException(AgentOzErrorCode.AGENT_NOT_FOUND, finalAgentId);
            }

            AgentConfigEntity config = agentConfigRepository.selectOne(
                    new LambdaQueryWrapper<AgentConfigEntity>()
                            .eq(AgentConfigEntity::getConfigId, agent.getConfigId())
            );

            if (config == null) {
                throw new AgentOzException(AgentOzErrorCode.CONFIG_NOT_FOUND, agent.getConfigId());
            }

            // 动态注入系统级 MCP
            try {
                String originalMcpJson = config.getMcpConfigJson();
                String injectedMcpJson = injectSystemMcp(originalMcpJson, agent.getAgentId(), agent.getConversationId());
                config.setMcpConfigJson(injectedMcpJson);
            } catch (Exception e) {
                log.error("注入系统MCP失败", e);
            }

            List<HistoryItem> historyItems = parseActiveContext(agent.getActiveContext());

            log.info("准备调用Codex: agentId={}, model={}, historySize={}", 
                    finalAgentId, config.getLlmModel(), historyItems.size());

            codexAgentClient.runTask(
                    agent.getConversationId(),
                    config,
                    historyItems,
                    request.getMessage(),
                    StreamGuard.wrapObserver(responseObserver, proto -> {
                        TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);
                        responseObserver.onNext(dto);
                    }, traceInfo)
            );
        }, traceInfo);
    }
    
    private String injectSystemMcp(String originalJson, String agentId, String conversationId) {
        try {
            ObjectNode rootNode;
            if (originalJson == null || originalJson.trim().isEmpty()) {
                rootNode = objectMapper.createObjectNode();
            } else {
                JsonNode node = objectMapper.readTree(originalJson);
                rootNode = node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
            }
            
            ObjectNode serversNode = rootNode.has("mcp_servers") ? (ObjectNode) rootNode.get("mcp_servers") : rootNode.putObject("mcp_servers");
            String token = jwtUtils.generateToken(agentId, conversationId);
            
            ObjectNode sysMcpConfig = objectMapper.createObjectNode();
            sysMcpConfig.put("url", websiteUrl + "/mcp/sys/sse");
            ObjectNode headers = sysMcpConfig.putObject("http_headers");
            headers.put("Authorization", "Bearer " + token);
            
            serversNode.set("agentoz_system", sysMcpConfig);
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("Failed to inject system MCP", e);
            return originalJson;
        }
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override public void onNext(StreamChatRequest value) {}
            @Override public void onError(Throwable t) { responseObserver.onError(t); }
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }

    private List<HistoryItem> parseActiveContext(String activeContextJson) {
        if (activeContextJson == null || activeContextJson.isEmpty() || "null".equals(activeContextJson)) {
            return List.of();
        }
        try {
            List<com.deepknow.agentoz.dto.MessageDTO> messageDTOs = objectMapper.readValue(
                    activeContextJson,
                    new TypeReference<List<com.deepknow.agentoz.dto.MessageDTO>>() {}
            );
            return HistoryProtoConverter.toHistoryItemList(messageDTOs);
        } catch (Exception e) {
            log.warn("解析上下文失败: {}", e.getMessage());
            return List.of();
        }
    }
}