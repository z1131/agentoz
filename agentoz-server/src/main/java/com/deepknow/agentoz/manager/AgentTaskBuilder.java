package com.deepknow.agentoz.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import codex.agent.RunTaskRequest;
import codex.agent.SessionConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 任务构建器
 *
 * <p>职责：</p>
 * <ul>
 *   <li>加载 Agent 和 Config 配置</li>
 *   <li>注入 MCP 服务器配置和请求头</li>
 *   <li>生成 JWT Token</li>
 *   <li>构建完整的 RunTaskRequest</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskBuilder {

    private final AgentRepository agentRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final JwtUtils jwtUtils;

    private final String websiteUrl = "https://agentoz.deepknow.online";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * 任务请求上下文
     */
    public record TaskContext(
            String agentId,
            String conversationId,
            String userMessage,
            String taskId,
            boolean isSubTask
    ) {}

    /**
     * 构建任务请求
     *
     * @param context 任务上下文
     * @return RunTaskRequest
     */
    public RunTaskRequest buildTaskRequest(TaskContext context) {
        log.info("[TaskBuilder] 开始构建任务请求: agentId={}, taskId={}", context.agentId(), context.taskId());

        // 1. 加载 Agent 配置
        AgentEntity agent = loadAgent(context.agentId());
        AgentConfigEntity config = loadConfig(agent.getConfigId());

        // 2. 注入 MCP 配置（添加自定义 MCP 服务器和请求头）
        injectMcpConfig(config, agent.getAgentId(), agent.getConversationId(), context.taskId());

        // 3. 构建 RunTaskRequest
        SessionConfig sessionConfig = com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter.toSessionConfig(config);

        RunTaskRequest request = RunTaskRequest.newBuilder()
                .setRequestId(java.util.UUID.randomUUID().toString())
                .setSessionId(agent.getConversationId())
                .setPrompt(context.userMessage())
                .setSessionConfig(sessionConfig)
                .setHistoryRollout(ByteString.copyFrom(agent.getActiveContextBytes()))
                .build();

        log.info("[TaskBuilder] 任务请求构建完成: requestId={}, sessionId={}, historySize={} bytes",
                request.getRequestId(),
                request.getSessionId(),
                request.getHistoryRollout().size());

        return request;
    }

    /**
     * 加载 Agent 实体
     */
    public AgentEntity loadAgent(String agentId) {
        AgentEntity agent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getAgentId, agentId)
        );

        if (agent == null) {
            throw new RuntimeException("Agent 不存在: " + agentId);
        }

        return agent;
    }

    /**
     * 加载 Agent 配置
     */
    private AgentConfigEntity loadConfig(String configId) {
        AgentConfigEntity config = agentConfigRepository.selectOne(
                new LambdaQueryWrapper<AgentConfigEntity>()
                        .eq(AgentConfigEntity::getConfigId, configId)
        );

        if (config == null) {
            throw new RuntimeException("Agent 配置不存在: " + configId);
        }

        return config;
    }

    /**
     * 注入 MCP 配置和请求头
     *
     * <p>功能：</p>
     * <ul>
     *   <li>为所有 MCP 服务器添加统一的请求头（X-Agent-ID, X-Conversation-ID）</li>
     *   <li>注入 agentoz_system 自定义 MCP 服务器</li>
     *   <li>生成 JWT Token 用于认证</li>
     * </ul>
     */
    public void injectMcpConfig(AgentConfigEntity config, String agentId, String conversationId, String taskId) {
        try {
            // 解析现有的 MCP 配置
            ObjectNode root = (config.getMcpConfigJson() == null || config.getMcpConfigJson().isEmpty())
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(config.getMcpConfigJson());

            // 获取或创建 mcp_servers 节点
            ObjectNode mcpServers = root.has("mcp_servers")
                    ? (ObjectNode) root.get("mcp_servers")
                    : root;

            // 为每个 MCP 服务器注入请求头
            mcpServers.fieldNames().forEachRemaining(serverName -> {
                JsonNode serverConfig = mcpServers.get(serverName);
                if (serverConfig.isObject()) {
                    ObjectNode serverObj = (ObjectNode) serverConfig;

                    // 获取或创建 http_headers
                    ObjectNode headers = serverObj.has("http_headers")
                            ? (ObjectNode) serverObj.get("http_headers")
                            : objectMapper.createObjectNode();

                    // 注入统一请求头（避免重复添加）
                    if (!headers.has("X-Agent-ID")) {
                        headers.put("X-Agent-ID", agentId);
                    }
                    if (!headers.has("X-Conversation-ID")) {
                        headers.put("X-Conversation-ID", conversationId);
                    }

                    serverObj.set("http_headers", headers);
                }
            });

            // 生成 JWT Token
            String token = jwtUtils.generateToken(agentId, conversationId);

            // 注入 agentoz_system 自定义 MCP 服务器
            ObjectNode systemServer = objectMapper.createObjectNode();
            systemServer.put("server_type", "streamable_http");
            systemServer.put("url", websiteUrl + "/mcp/message");

            ObjectNode systemHeaders = objectMapper.createObjectNode();
            systemHeaders.put("Authorization", "Bearer " + token);
            systemHeaders.put("X-Agent-ID", agentId);
            systemHeaders.put("X-Conversation-ID", conversationId);

            systemServer.set("http_headers", systemHeaders);
            mcpServers.set("agentoz_system", systemServer);

            // 更新配置
            config.setMcpConfigJson(objectMapper.writeValueAsString(root));

            log.debug("[TaskBuilder] MCP 配置注入完成: servers={}", mcpServers.size());

        } catch (Exception e) {
            log.error("[TaskBuilder] MCP 配置注入失败: agentId={}, error={}", agentId, e.getMessage(), e);
            // 不抛异常，使用默认配置
        }
    }
}
