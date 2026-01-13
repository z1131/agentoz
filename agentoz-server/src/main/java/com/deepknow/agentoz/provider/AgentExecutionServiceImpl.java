package com.deepknow.agentoz.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.infra.converter.grpc.TaskResponseProtoConverter;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.StreamGuard;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.infra.history.ConversationHistoryManager;
import com.deepknow.agentoz.infra.history.AgentContextManager;
import codex.agent.RunTaskRequest;
import codex.agent.UserInput;
import codex.agent.RunTaskResponse;
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
            String role = request.getRole() != null ? request.getRole() : "user";

            log.info("收到任务请求: {}, Role={}", traceInfo, role);

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

            // 步骤 1: 记录消息到公共会话历史 (不论角色，始终记录，支持 AgentName 显示)
            String historyRole = (request.getSenderName() != null) ? request.getSenderName() : "user";
            conversationHistoryManager.appendUserMessage(conversationId, userMessage, historyRole);

            // 步骤 2: 记录当前 Agent 被调用状态 (优先使用 SenderName 作为 Role 标识，用于状态描述)
            String contextRole = (request.getSenderName() != null) ? request.getSenderName() : request.getRole();
            if (contextRole == null) contextRole = "user";
            agentContextManager.onAgentCalled(finalAgentId, userMessage, contextRole);

            // 动态注入系统级 MCP
            try {
                String originalMcpJson = config.getMcpConfigJson();
                String injectedMcpJson = injectSystemMcp(originalMcpJson, agent.getAgentId(), agent.getConversationId());
                config.setMcpConfigJson(injectedMcpJson);
            } catch (Exception e) {
                log.error("注入系统MCP失败", e);
            }

            // 步骤 3: 提取历史记录 (透传模式：直接提取 JSON 数组中的每一项)
            List<String> historyJsonList = extractHistoryJsonList(agent.getActiveContext());

            log.info("准备调用Codex: agentId={}, model={}, historySize={}",
                    finalAgentId, config.getLlmModel(), historyJsonList.size());

            // 步骤 4: 调用 Codex-Agent，并在响应返回时记录历史
            RunTaskRequest requestParams = RunTaskRequest.newBuilder()
                    .setConversationId(agent.getConversationId())
                    .setConfig(ConfigProtoConverter.toSessionConfig(config))
                    .addAllHistoryJson(historyJsonList) // 使用透传的 JSON 列表
                    .setInput(UserInput.newBuilder().setText(userMessage).build())
                    .build();

            codexAgentClient.runTask(
                    agent.getConversationId(),
                    requestParams,
                    StreamGuard.wrapObserver(responseObserver, (RunTaskResponse proto) -> {
                        // 每次收到响应时
                        TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);

                        // 记录 Assistant 响应到会话历史 (使用 Agent 的真实名称)
                        if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
                            conversationHistoryManager.appendAssistantMessage(conversationId, dto.getFinalResponse(), agent.getAgentName());
                        }

                        // 核心：将 Codex 返回的新 Item (JSON) 直接追加到 Agent 的 activeContext
                        if (proto.getNewItemsJsonCount() > 0) {
                            for (String itemJson : proto.getNewItemsJsonList()) {
                                agent.appendContext(itemJson, objectMapper);
                            }
                            // 异步更新 Agent 状态描述 (仅输出时)
                            if (dto.getFinalResponse() != null && !dto.getFinalResponse().isEmpty()) {
                                agent.updateOutputState(dto.getFinalResponse());
                            }
                            agentRepository.updateById(agent);
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
            sysMcpConfig.put("url", "https://agentoz.deepknow.online/mcp");
            
            ObjectNode httpHeaders = sysMcpConfig.putObject("http_headers");
            httpHeaders.put("Authorization", "Bearer " + token);
            
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

    /**
     * 辅助方法：将数据库存的 JSON 数组字符串拆分为 String 列表
     */
    private List<String> extractHistoryJsonList(String activeContextJson) {
        if (activeContextJson == null || activeContextJson.isEmpty() || "null".equals(activeContextJson)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(activeContextJson);
            List<String> items = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    items.add(item.isTextual() ? item.asText() : item.toString());
                }
            }
            return items;
        } catch (Exception e) {
            log.warn("解析上下文 JSON 列表失败: {}", e.getMessage());
            return List.of();
        }
    }
}
