package com.deepknow.agentoz.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.dto.A2AContext;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.infra.converter.grpc.InternalCodexEventConverter;
import com.deepknow.agentoz.infra.history.AgentContextManager;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.ConversationEntity;
import codex.agent.RunTaskRequest;
import codex.agent.SessionConfig;
import codex.agent.ModelProviderInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Agent 执行管理器（核心业务逻辑层）
 *
 * <h3>🎯 职责</h3>
 * <ul>
 *   <li>调用 Codex-Agent 并处理事件流</li>
 *   <li>管理 Agent 状态（上下文持久化）</li>
 *   <li>维护会话历史（用于业务展示）</li>
 * </ul>
 *
 * <h3>📦 输出</h3>
 * <p>InternalCodexEvent - 对齐 Codex 原始事件，供 provider 层转换为 API DTO</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutionManager {

    private final AgentRepository agentRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final ConversationRepository conversationRepository;
    private final CodexAgentClient codexAgentClient;
    private final AgentContextManager agentContextManager;
    private final JwtUtils jwtUtils;
    private final A2ATaskRegistry a2aTaskRegistry;

    private final String websiteUrl = "https://agentoz.deepknow.online";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行任务请求上下文
     */
    public record ExecutionContext(
            String agentId,
            String conversationId,
            String userMessage,
            String role,
            String senderName
    ) {}

    /**
     * 执行任务请求上下文（扩展版 - 支持 A2A 协议上下文）
     */
    public record ExecutionContextExtended(
            String agentId,
            String conversationId,
            String userMessage,
            String role,
            String senderName,
            boolean isSubTask,  // 是否为子任务（保留兼容性）
            A2AContext a2aContext // ⭐ A2A 协议上下文
    ) {
        public ExecutionContextExtended(String agentId, String conversationId, String userMessage, String role, String senderName) {
            this(agentId, conversationId, userMessage, role, senderName, false, null);
        }

        public ExecutionContextExtended(String agentId, String conversationId, String userMessage, String role, String senderName, boolean isSubTask) {
            this(agentId, conversationId, userMessage, role, senderName, isSubTask, null);
        }

        public ExecutionContext toExecutionContext() {
            return new ExecutionContext(agentId, conversationId, userMessage, role, senderName);
        }
    }

    /**
     * 执行任务 - 核心业务逻辑
     *
     * @param context 执行上下文
     * @param eventConsumer 事件消费者（流式回调）
     * @param onCompleted 完成回调
     * @param onError 错误回调
     */
    public void executeTask(
            ExecutionContext context,
            Consumer<InternalCodexEvent> eventConsumer,
            Runnable onCompleted,
            Consumer<Throwable> onError
    ) {
        executeTaskExtended(new ExecutionContextExtended(
                context.agentId(),
                context.conversationId(),
                context.userMessage(),
                context.role(),
                context.senderName(),
                false  // 默认不是子任务
        ), eventConsumer, onCompleted, onError);
    }

    /**
     * 执行任务 - 扩展版（支持子任务标识）
     *
     * @param context 执行上下文（扩展版）
     * @param eventConsumer 事件消费者（流式回调）
     * @param onCompleted 完成回调
     * @param onError 错误回调
     */
    public void executeTaskExtended(
            ExecutionContextExtended context,
            Consumer<InternalCodexEvent> eventConsumer,
            Runnable onCompleted,
            Consumer<Throwable> onError
    ) {
        // 1. 初始化或提取 A2A 上下文
        A2AContext a2aContext = context.a2aContext();
        if (a2aContext == null) {
            a2aContext = A2AContext.root(context.agentId(), null);
        }

        String traceInfo = String.format("ConvId=%s, TraceId=%s, Depth=%d", 
                context.conversationId(), a2aContext.getTraceId(), a2aContext.getDepth());

        // 2. 判定子任务状态
        boolean isSubTask = context.isSubTask() || a2aContext.getDepth() > 0;
        
        // 生成或提取当前 TaskID
        final String currentTaskId = (a2aContext.getDepth() == 0) ? context.conversationId() : UUID.randomUUID().toString();

        try {
            // 3. 注册任务到 A2A 注册表
            a2aTaskRegistry.registerTask(A2ATaskRegistry.TaskRecord.builder()
                    .taskId(currentTaskId)
                    .conversationId(context.conversationId())
                    .a2aContext(a2aContext)
                    .eventConsumer(eventConsumer)
                    .startTime(System.currentTimeMillis())
                    .build());
            
            log.info("[AgentExecutionManager] ✓ 任务已入库: {}, TaskId={}, SubTask={}", 
                    traceInfo, currentTaskId, isSubTask);

            // 4. 路由并加载 Agent
            String agentId = resolveAgentId(context);
            AgentEntity agent = loadAgent(agentId);
            AgentConfigEntity config = loadConfig(agent.getConfigId());

            // 5. 状态维护
            appendMessageToConversationHistory(
                    context.conversationId(),
                    context.role(),
                    context.userMessage(),
                    context.senderName() != null ? context.senderName() : "user"
            );

            String contextRole = (context.senderName() != null) ? context.senderName() : context.role();
            if (contextRole == null) contextRole = "user";
            agentContextManager.onAgentCalled(agentId, context.userMessage(), contextRole);

            // 6. 注入系统级 MCP 和 A2A 请求头
            injectSystemMcp(config, agent.getAgentId(), agent.getConversationId(), a2aContext, currentTaskId);

            // 7. 构建 Codex 请求
            byte[] historyRollout = agent.getActiveContextBytes();
            SessionConfig sessionConfig = ConfigProtoConverter.toSessionConfig(config);

            RunTaskRequest requestParams = RunTaskRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setSessionId(agent.getConversationId())
                    .setPrompt(context.userMessage())
                    .setSessionConfig(sessionConfig)
                    .setHistoryRollout(ByteString.copyFrom(historyRollout))
                    .build();

            final StringBuilder fullResponseBuilder = new StringBuilder();
            final String finalAgentId = agentId;

            // 8. 调用 Codex-Agent
            codexAgentClient.runTask(
                    agent.getConversationId(),
                    requestParams,
                    new StreamObserver<>() {
                        @Override
                        public void onNext(codex.agent.RunTaskResponse proto) {
                            try {
                                InternalCodexEvent event = InternalCodexEventConverter.toInternalEvent(proto);
                                if (event == null) return;
                                event.setSenderName(agent.getAgentName());

                                persistCompleteEvent(context.conversationId(), agent.getAgentName(), event);
                                collectTextResponse(event, fullResponseBuilder);

                                if (event.getStatus() == InternalCodexEvent.Status.FINISHED) {
                                    handleFinished(event, agent, finalAgentId, context.conversationId(), fullResponseBuilder);
                                }

                                eventConsumer.accept(event);
                            } catch (Exception e) {
                                log.error("处理 Codex 事件失败", e);
                                onError.accept(e);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            a2aTaskRegistry.unregisterTask(currentTaskId);
                            log.error("Codex 流错误回调触发: {}", traceInfo, t);
                            onError.accept(t);
                        }

                        @Override
                        public void onCompleted() {
                            a2aTaskRegistry.unregisterTask(currentTaskId);
                            log.info("Codex 流完成回调触发: {}", traceInfo);
                            onCompleted.run();
                        }
                    }
            );

        } catch (Exception e) {
            a2aTaskRegistry.unregisterTask(currentTaskId);
            log.error("执行任务失败: {}", traceInfo, e);
            onError.accept(e);
        }
    }

    // ==================== 私有方法 ====================

    private String resolveAgentId(ExecutionContextExtended context) {
        String agentId = context.agentId();
        if (agentId == null || agentId.isEmpty()) {
            AgentEntity primaryAgent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, context.conversationId())
                            .eq(AgentEntity::getIsPrimary, true)
            );
            if (primaryAgent == null) throw new AgentOzException(AgentOzErrorCode.PRIMARY_AGENT_MISSING, context.conversationId());
            agentId = primaryAgent.getAgentId();
        }
        return agentId;
    }

    private AgentEntity loadAgent(String agentId) {
        AgentEntity agent = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId));
        if (agent == null) throw new AgentOzException(AgentOzErrorCode.AGENT_NOT_FOUND, agentId);
        return agent;
    }

    private AgentConfigEntity loadConfig(String configId) {
        AgentConfigEntity config = agentConfigRepository.selectOne(new LambdaQueryWrapper<AgentConfigEntity>().eq(AgentConfigEntity::getConfigId, configId));
        if (config == null) throw new AgentOzException(AgentOzErrorCode.CONFIG_NOT_FOUND, configId);
        return config;
    }

    private void injectSystemMcp(AgentConfigEntity config, String agentId, String conversationId, A2AContext a2aContext, String currentTaskId) {
        try {
            String originalJson = config.getMcpConfigJson();
            ObjectNode rootNode = (originalJson == null || originalJson.trim().isEmpty()) ? objectMapper.createObjectNode() : (ObjectNode) objectMapper.readTree(originalJson);
            ObjectNode mcpRoot = (rootNode.has("mcp_servers")) ? (ObjectNode) rootNode.get("mcp_servers") : rootNode;

            mcpRoot.fieldNames().forEachRemaining(mcpName -> {
                JsonNode mcpConfig = mcpRoot.get(mcpName);
                if (mcpConfig.isObject()) {
                    ObjectNode headers = ((ObjectNode) mcpConfig).has("http_headers") ? (ObjectNode) ((ObjectNode) mcpConfig).get("http_headers") : objectMapper.createObjectNode();
                    headers.put("X-Agent-ID", agentId);
                    headers.put("X-Conversation-ID", conversationId);
                    
                    if (a2aContext != null) {
                        headers.put("X-A2A-Trace-ID", a2aContext.getTraceId());
                        // ⭐ 关键修正：将当前的 TaskID 注入为子任务的父 ID
                        headers.put("X-A2A-Parent-Task-ID", currentTaskId);
                        headers.put("X-A2A-Depth", String.valueOf(a2aContext.getDepth()));
                        headers.put("X-A2A-Origin-Agent-ID", a2aContext.getOriginAgentId());
                    }
                    ((ObjectNode) mcpConfig).set("http_headers", headers);
                }
            });

            String token = jwtUtils.generateToken(agentId, conversationId);
            ObjectNode sysMcpConfig = objectMapper.createObjectNode();
            sysMcpConfig.put("server_type", "streamable_http");
            sysMcpConfig.put("url", websiteUrl + "/mcp/message");
            ObjectNode sysHeaders = objectMapper.createObjectNode();
            sysHeaders.put("Authorization", "Bearer " + token);
            sysHeaders.put("X-Agent-ID", agentId);
            sysHeaders.put("X-Conversation-ID", conversationId);
            if (a2aContext != null) {
                sysHeaders.put("X-A2A-Trace-ID", a2aContext.getTraceId());
                sysHeaders.put("X-A2A-Depth", String.valueOf(a2aContext.getDepth()));
            }
            sysMcpConfig.set("http_headers", sysHeaders);
            mcpRoot.set("agentoz_system", sysMcpConfig);
            config.setMcpConfigJson(objectMapper.writeValueAsString(rootNode));
        } catch (Exception e) { log.error("注入系统MCP失败", e); }
    }

    private void collectTextResponse(InternalCodexEvent event, StringBuilder builder) {
        try {
            String eventType = event.getEventType();
            String rawJson = event.getRawEventJson();
            if (rawJson == null) return;
            JsonNode node = objectMapper.readTree(rawJson);
            if ("agent_message_delta".equals(eventType)) {
                if (node.path("delta").has("text")) builder.append(node.path("delta").path("text").asText());
            } else if ("agent_message".equals(eventType)) {
                JsonNode content = node.path("content");
                if (content.isArray()) {
                    builder.setLength(0);
                    for (JsonNode item : content) if (item.has("text")) builder.append(item.get("text").asText());
                }
            }
        } catch (Exception e) { log.debug("解析文本失败: {}", e.getMessage()); }
    }

    private void persistCompleteEvent(String conversationId, String senderName, InternalCodexEvent event) {
        try {
            String eventType = event.getEventType();
            String rawJson = event.getRawEventJson();
            if (eventType == null || rawJson == null) return;
            JsonNode node = objectMapper.readTree(rawJson);
            ObjectNode historyItem = null;
            if ("agent_message".equals(eventType)) historyItem = createAgentMessageItem(senderName, node);
            else if ("item_completed".equals(eventType)) historyItem = createToolCallItem(senderName, node);
            else if ("agent_reasoning".equals(eventType)) historyItem = createReasoningItem(senderName, node);

            if (historyItem != null) {
                appendHistoryItem(conversationId, historyItem);
                if (event.getDisplayItems() == null) event.setDisplayItems(new java.util.ArrayList<>());
                event.getDisplayItems().add(historyItem.toString());
            }
        } catch (Exception e) { log.warn("持久化失败: {}", e.getMessage()); }
    }

    private ObjectNode createAgentMessageItem(String senderName, JsonNode node) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", UUID.randomUUID().toString());
        item.put("type", "AgentMessage");
        item.put("sender", senderName);
        item.put("timestamp", LocalDateTime.now().toString());
        ArrayNode content = objectMapper.createArrayNode();
        for (JsonNode c : node.path("content")) {
            if (c.has("text")) {
                ObjectNode textNode = objectMapper.createObjectNode();
                textNode.put("type", "text");
                textNode.put("text", c.get("text").asText());
                content.add(textNode);
            }
        }
        item.set("content", content);
        return item;
    }

    private ObjectNode createToolCallItem(String senderName, JsonNode node) {
        JsonNode toolItem = node.path("item");
        if (toolItem.isMissingNode()) return null;
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", UUID.randomUUID().toString());
        item.put("type", "McpToolCall");
        item.put("sender", senderName);
        item.put("timestamp", LocalDateTime.now().toString());
        item.put("tool", toolItem.path("name").asText("unknown"));
        item.set("arguments", toolItem.path("arguments"));
        item.set("result", toolItem.path("result"));
        return item;
    }

    private ObjectNode createReasoningItem(String senderName, JsonNode node) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", UUID.randomUUID().toString());
        item.put("type", "AgentMessage");
        item.put("sender", senderName);
        item.put("timestamp", LocalDateTime.now().toString());
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", "> [Thinking] " + node.path("content").asText(""));
        content.add(textNode);
        item.set("content", content);
        return item;
    }

    private void appendHistoryItem(String conversationId, ObjectNode historyItem) {
        try {
            ConversationEntity conversation = conversationRepository.selectOne(new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getConversationId, conversationId));
            if (conversation == null) return;
            String currentHistory = conversation.getHistoryContext();
            ArrayNode historyNode = (currentHistory == null || currentHistory.isEmpty() || "null".equals(currentHistory)) ? objectMapper.createArrayNode() : (ArrayNode) objectMapper.readTree(currentHistory);
            historyNode.add(historyItem);
            conversation.setHistoryContext(objectMapper.writeValueAsString(historyNode));
            if ("AgentMessage".equals(historyItem.get("type").asText())) {
                String text = historyItem.path("content").path(0).path("text").asText("");
                conversation.setLastMessageContent(truncateText(text, 500));
                conversation.setLastMessageType("assistant");
            }
            conversation.setLastMessageAt(LocalDateTime.now());
            Integer count = conversation.getMessageCount();
            conversation.setMessageCount(count != null ? count + 1 : 1);
            conversationRepository.updateById(conversation);
        } catch (Exception e) { log.error("追加历史失败", e); }
    }

    private void handleFinished(InternalCodexEvent event, AgentEntity agent, String agentId, String conversationId, StringBuilder fullResponseBuilder) {
        byte[] rollout = event.getUpdatedRollout();
        if (rollout == null || rollout.length == 0) return;
        agent.setActiveContextFromBytes(rollout);
        String finalResponse = fullResponseBuilder.toString();
        if (!finalResponse.isEmpty()) agent.updateOutputState(finalResponse);
        agentRepository.updateById(agent);
    }

    private void appendMessageToConversationHistory(String conversationId, String role, String content, String senderName) {
        ObjectNode messageItem = objectMapper.createObjectNode();
        messageItem.put("id", UUID.randomUUID().toString());
        messageItem.put("type", "assistant".equals(role) ? "AgentMessage" : "UserMessage");
        messageItem.put("sender", senderName);
        messageItem.put("timestamp", LocalDateTime.now().toString());
        ArrayNode contentArray = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", content);
        contentArray.add(textContent);
        messageItem.set("content", contentArray);
        appendHistoryItem(conversationId, messageItem);
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}