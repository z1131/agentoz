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
import com.deepknow.agentoz.infra.history.AgentContextManager;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import codex.agent.RunTaskRequest;
import codex.agent.RunTaskResponse;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.ConversationEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 执行服务实现 (数据面)
 *
 * <h3>🔄 新版设计（对齐 Codex Adapter）</h3>
 * <ul>
 *   <li>使用 history_rollout (bytes) 传递会话状态，而非 JSON 数组</li>
 *   <li>接收 updated_rollout (bytes) 更新 Agent 的 activeContext</li>
 *   <li>解析 codex_event_json 事件以实现流式输出</li>
 * </ul>
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
    private ConversationRepository conversationRepository;

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

            // 路由到目标 Agent
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

            // 步骤 1: 追加用户消息到 ConversationEntity.historyContext（用于业务展示）
            appendMessageToConversationHistory(conversationId, "user", userMessage,
                request.getSenderName() != null ? request.getSenderName() : "user");

            // 步骤 2: 记录当前 Agent 被调用状态
            String contextRole = (request.getSenderName() != null) ? request.getSenderName() : request.getRole();
            if (contextRole == null) contextRole = "user";
            agentContextManager.onAgentCalled(finalAgentId, userMessage, contextRole);

            // 步骤 3: 动态注入系统级 MCP
            try {
                String originalMcpJson = config.getMcpConfigJson();
                String injectedMcpJson = injectSystemMcp(originalMcpJson, agent.getAgentId(), agent.getConversationId());
                config.setMcpConfigJson(injectedMcpJson);
            } catch (Exception e) {
                log.error("注入系统MCP失败", e);
            }

            // 步骤 4: 获取 Agent 的历史会话状态（JSONL bytes）
            byte[] historyRollout = agent.getActiveContextBytes();
            log.info("准备调用Codex: agentId={}, model={}, historySize={} bytes",
                    finalAgentId, config.getLlmModel(), historyRollout.length);

            // 步骤 5: 构建新版 RunTaskRequest（对齐 adapter.proto）
            RunTaskRequest requestParams = RunTaskRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setSessionId(agent.getConversationId())  // session_id = conversation_id
                    .setPrompt(userMessage)                    // prompt 替代旧的 input.text
                    .setSessionConfig(ConfigProtoConverter.toSessionConfig(config))
                    .setHistoryRollout(ByteString.copyFrom(historyRollout))  // bytes 替代 repeated string
                    .build();

            // 步骤 6: 调用 Codex-Agent，处理事件驱动的响应流
            final StringBuilder fullResponseBuilder = new StringBuilder();

            codexAgentClient.runTask(
                    agent.getConversationId(),
                    requestParams,
                    StreamGuard.wrapObserver(responseObserver, (RunTaskResponse proto) -> {
                        // 转换响应
                        TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);

                        // 收集完整响应内容
                        if (dto.getTextDelta() != null) {
                            fullResponseBuilder.append(dto.getTextDelta());
                        }
                        if (dto.getFinalResponse() != null) {
                            fullResponseBuilder.setLength(0);
                            fullResponseBuilder.append(dto.getFinalResponse());
                        }

                        // 核心：处理 updated_rollout（流结束标志）
                        if (dto.getUpdatedRollout() != null && dto.getUpdatedRollout().length > 0) {
                            // 更新 Agent 的 activeContext
                            agent.setActiveContextFromBytes(dto.getUpdatedRollout());

                            // 记录 Assistant 响应到会话历史（用于业务展示）
                            String finalResponse = fullResponseBuilder.toString();
                            if (!finalResponse.isEmpty()) {
                                appendMessageToConversationHistory(conversationId, "assistant",
                                        finalResponse, agent.getAgentName());
                                agent.updateOutputState(finalResponse);
                            }

                            // 持久化 Agent 状态
                            agentRepository.updateById(agent);
                            log.info("Agent 状态已更新: agentId={}, rolloutSize={} bytes",
                                    finalAgentId, dto.getUpdatedRollout().length);
                        }

                        // 转发响应给调用方
                        responseObserver.onNext(dto);
                    }, traceInfo)
            );
        }, traceInfo);
    }

    /**
     * 注入系统级 MCP 配置
     *
     * <p>添加 agentoz_system MCP 服务器，用于 Agent 间协作</p>
     */
    private String injectSystemMcp(String originalJson, String agentId, String conversationId) {
        try {
            ObjectNode rootNode;
            if (originalJson == null || originalJson.trim().isEmpty()) {
                rootNode = objectMapper.createObjectNode();
            } else {
                JsonNode node = objectMapper.readTree(originalJson);
                rootNode = node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
            }

            String token = jwtUtils.generateToken(agentId, conversationId);

            // 构建 System MCP 配置（对齐 adapter.proto 的 McpServerDef）
            ObjectNode sysMcpConfig = objectMapper.createObjectNode();
            sysMcpConfig.put("server_type", "streamable_http");
            sysMcpConfig.put("url", websiteUrl + "/mcp");

            // 注意：http_headers 在 adapter.proto 中是 ModelProviderInfo 的字段
            // MCP 配置中通常通过其他方式传递认证信息

            rootNode.set("agentoz_system", sysMcpConfig);
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
     * 追加消息到会话历史 (JSON格式)
     *
     * <p>⚠️ 这是用于业务展示的全量历史，不参与 Codex 计算</p>
     *
     * @param conversationId 会话ID
     * @param role 角色 (user/assistant)
     * @param content 消息内容
     * @param senderName 发送者名称 (用于显示)
     */
    private void appendMessageToConversationHistory(String conversationId, String role, String content, String senderName) {
        try {
            ConversationEntity conversation = conversationRepository.selectOne(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getConversationId, conversationId)
            );

            if (conversation == null) {
                log.warn("会话不存在: conversationId={}", conversationId);
                return;
            }

            // 构造展示用的消息格式
            ObjectNode messageItem = objectMapper.createObjectNode();
            messageItem.put("type", "message");
            messageItem.put("role", role);
            messageItem.put("sender", senderName);
            messageItem.put("timestamp", LocalDateTime.now().toString());

            // 构造 content 数组
            ObjectNode contentItem = objectMapper.createObjectNode();
            contentItem.put("type", "assistant".equals(role) ? "output_text" : "input_text");
            contentItem.put("text", content);
            messageItem.set("content", objectMapper.createArrayNode().add(contentItem));

            // 追加到 historyContext
            String currentHistory = conversation.getHistoryContext();
            if (currentHistory == null || currentHistory.isEmpty() || "null".equals(currentHistory)) {
                currentHistory = "[]";
            }

            JsonNode historyNode = objectMapper.readTree(currentHistory);
            if (historyNode.isArray()) {
                ((ArrayNode) historyNode).add(messageItem);
                conversation.setHistoryContext(objectMapper.writeValueAsString(historyNode));

                // 更新辅助字段
                conversation.setLastMessageAt(LocalDateTime.now());
                conversation.setLastMessageType(role);
                conversation.setLastMessageContent(truncateText(content, 500));

                Integer count = conversation.getMessageCount();
                conversation.setMessageCount(count != null ? count + 1 : 1);

                conversationRepository.updateById(conversation);
            }
        } catch (Exception e) {
            log.error("追加消息到会话历史失败: conversationId={}", conversationId, e);
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
