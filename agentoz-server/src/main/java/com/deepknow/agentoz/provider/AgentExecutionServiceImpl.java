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
import com.deepknow.agentoz.infra.history.ConversationHistoryManager;
import com.deepknow.agentoz.infra.history.AgentContextManager;
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

    @Autowired
    private ConversationHistoryManager conversationHistoryManager;

    @Autowired
    private AgentContextManager agentContextManager;

    private final String websiteUrl = "https://agentoz.deepknow.online";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String traceInfo = "ConvId=" + request.getConversationId();

        StreamGuard.run(responseObserver, () -> {
            String agentId = request.getAgentId();
            String conversationId = request.getConversationId();
            String userMessage = request.getMessage();

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

            // ✅ 步骤 1: 记录用户消息到会话历史（所有 Agent 共享）
            conversationHistoryManager.appendUserMessage(conversationId, userMessage);

            // ✅ 步骤 2: 广播用户消息到会话中的所有 Agent
            broadcastUserMessageToAllAgents(conversationId, userMessage);

            // ✅ 步骤 3: 记录主智能体被调用状态（已在步骤2的broadcast中记录）

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

            // ✅ 步骤 4: 调用 Codex-Agent，并在响应返回时记录历史
            codexAgentClient.runTask(
                    agent.getConversationId(),
                    config,
                    historyItems,
                    userMessage,
                    StreamGuard.wrapObserver(responseObserver, proto -> {
                        // 每次收到响应时
                        TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);

                        // ✅ 记录 Assistant 响应到会话历史
                        if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
                            conversationHistoryManager.appendAssistantMessage(conversationId, dto.getFinalResponse());

                            // ✅ 记录主智能体返回状态
                            agentContextManager.onAgentResponse(finalAgentId, dto.getFinalResponse());
                        }

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
            
            // 构建 System MCP 配置
            ObjectNode sysMcpConfig = objectMapper.createObjectNode();
            // 适配 Codex 偏好的 streamable_http 模式
            sysMcpConfig.put("type", "streamable_http");
            // 使用新的 MCP SDK Server 端点（基于官方 MCP Java SDK）
            sysMcpConfig.put("url", "https://agentoz.deepknow.online/mcp/agent/message");
            
            ObjectNode headers = sysMcpConfig.putObject("http_headers");
            headers.put("Authorization", "Bearer " + token);
            
            serversNode.set("agentoz_system", sysMcpConfig);
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("Failed to inject system MCP", e);
            return originalJson;
        }
    }

    /**
     * 广播用户消息到会话中的所有 Agent
     *
     * <p>用于 executeTask 方法，将用户消息追加到所有 Agent 的 activeContext</p>
     *
     * @param conversationId 会话 ID
     * @param userMessage 用户消息
     */
    private void broadcastUserMessageToAllAgents(String conversationId, String userMessage) {
        try {
            // 查询会话中的所有 Agent
            List<AgentEntity> allAgents = agentRepository.selectList(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
            );

            if (allAgents == null || allAgents.isEmpty()) {
                log.warn("会话中没有找到任何 Agent: conversationId={}", conversationId);
                return;
            }

            log.info("广播用户消息到 {} 个 Agent", allAgents.size());

            // 对每个 Agent 记录用户消息
            for (AgentEntity agent : allAgents) {
                agentContextManager.onAgentCalled(agent.getAgentId(), userMessage);
            }

            log.info("✅ 用户消息已广播到所有 Agent");

        } catch (Exception e) {
            log.error("❌ 广播用户消息到所有 Agent 失败: conversationId={}", conversationId, e);
        }
    }

    @Override
    public void executeTaskToSingleAgent(String agentId, String conversationId, String message,
                                         StreamObserver<TaskResponse> responseObserver) {
        String traceInfo = "ConvId=" + conversationId + ",AgentId=" + agentId;

        StreamGuard.run(responseObserver, () -> {
            log.info(">>> 收到单Agent任务请求: {}", traceInfo);

            // 参数校验
            if (agentId == null || agentId.isEmpty()) {
                throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM, "agentId 不能为空");
            }
            if (conversationId == null || conversationId.isEmpty()) {
                throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM, "conversationId 不能为空");
            }
            if (message == null || message.isEmpty()) {
                throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM, "message 不能为空");
            }

            // 查询指定的 Agent
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                throw new AgentOzException(AgentOzErrorCode.AGENT_NOT_FOUND, agentId);
            }

            // 验证 Agent 是否属于指定的会话
            if (!conversationId.equals(agent.getConversationId())) {
                throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM,
                        "Agent 不属于指定的会话: agentId=" + agentId + ", conversationId=" + conversationId);
            }

            // 查询配置
            AgentConfigEntity config = agentConfigRepository.selectOne(
                    new LambdaQueryWrapper<AgentConfigEntity>()
                            .eq(AgentConfigEntity::getConfigId, agent.getConfigId())
            );

            if (config == null) {
                throw new AgentOzException(AgentOzErrorCode.CONFIG_NOT_FOUND, agent.getConfigId());
            }

            // ⚠️ 注意：不追加到会话历史（因为是 Agent 间调用）

            // ✅ 步骤 1: 仅记录目标 Agent 被调用状态
            agentContextManager.onAgentCalled(agentId, message);

            // 动态注入系统级 MCP
            try {
                String originalMcpJson = config.getMcpConfigJson();
                String injectedMcpJson = injectSystemMcp(originalMcpJson, agent.getAgentId(), agent.getConversationId());
                config.setMcpConfigJson(injectedMcpJson);
            } catch (Exception e) {
                log.error("注入系统MCP失败", e);
            }

            List<HistoryItem> historyItems = parseActiveContext(agent.getActiveContext());

            log.info("准备调用Codex (单Agent模式): agentId={}, model={}, historySize={}",
                    agentId, config.getLlmModel(), historyItems.size());

            // ✅ 步骤 2: 调用 Codex-Agent，并在响应返回时记录历史
            codexAgentClient.runTask(
                    agent.getConversationId(),
                    config,
                    historyItems,
                    message,
                    StreamGuard.wrapObserver(responseObserver, proto -> {
                        // 每次收到响应时
                        TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);

                        // ⚠️ 注意：不追加到会话历史（因为是 Agent 间调用）

                        // ✅ 仅记录目标 Agent 返回状态
                        if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
                            agentContextManager.onAgentResponse(agentId, dto.getFinalResponse());
                        }

                        responseObserver.onNext(dto);
                    }, traceInfo)
            );
        }, traceInfo);
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