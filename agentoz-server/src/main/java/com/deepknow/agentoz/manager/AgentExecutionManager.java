package com.deepknow.agentoz.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.infra.converter.grpc.InternalCodexEventConverter;
import com.deepknow.agentoz.infra.history.AgentContextManager;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
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
    private final SessionStreamRegistry sessionStreamRegistry;

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
     * 执行任务请求上下文（扩展版 - 支持子任务标识）
     */
    public record ExecutionContextExtended(
            String agentId,
            String conversationId,
            String userMessage,
            String role,
            String senderName,
            boolean isSubTask  // 是否为子任务（通过 call_agent 调用）
    ) {
        public ExecutionContextExtended(String agentId, String conversationId, String userMessage, String role, String senderName) {
            this(agentId, conversationId, userMessage, role, senderName, false);
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
        String traceInfo = "ConvId=" + context.conversationId();

        try {
            // 0. 注册会话通道 (仅主任务注册，子任务不注册)
            // 判断逻辑：如果调用栈中有 CallAgentTool，则认为是子任务
            boolean isSubTask = context.isSubTask() || isCalledFromCallAgentTool();

            if (!isSubTask) {
                sessionStreamRegistry.register(context.conversationId(), eventConsumer);
                log.info("[AgentExecutionManager] ✓ 注册主任务流: conversationId={}", context.conversationId());
            } else {
                log.debug("[AgentExecutionManager] ⊗ 跳过子任务注册: conversationId={}, 来自CallAgentTool",
                        context.conversationId());
            }

            // 1. 路由到目标 Agent
            String agentId = resolveAgentId(context);
            log.info("执行任务: agentId={}, {}", agentId, traceInfo);

            // 2. 加载 Agent 和配置
            AgentEntity agent = loadAgent(agentId);
            AgentConfigEntity config = loadConfig(agent.getConfigId());

            // 3. 追加消息到会话历史（用于业务展示）
            appendMessageToConversationHistory(
                    context.conversationId(),
                    context.role(),  // ✅ 使用实际角色（user/assistant），区分用户输入和智能体调用
                    context.userMessage(),
                    context.senderName() != null ? context.senderName() : "user"
            );

            // 4. 记录 Agent 被调用状态
            String contextRole = (context.senderName() != null) ? context.senderName() : context.role();
            if (contextRole == null) contextRole = "user";
            agentContextManager.onAgentCalled(agentId, context.userMessage(), contextRole);

            // 5. 动态注入系统级 MCP
            injectSystemMcp(config, agent.getAgentId(), agent.getConversationId());

            // 6. 获取 Agent 的历史会话状态
            byte[] historyRollout = agent.getActiveContextBytes();
            log.info("准备调用Codex: agentId={}, model={}, historySize={} bytes",
                    agentId, config.getLlmModel(), historyRollout.length);

                // 7. 🔧 简单策略：有历史记录就不传配置
                // 原因：配置不支持变更，history_rollout中已包含完整配置
                boolean hasHistory = (historyRollout != null && historyRollout.length > 0);

                SessionConfig sessionConfig;

                if (hasHistory) {
                    // 有历史记录，发送空配置（避免重复发送指令）
                    log.info("⏩ 检测到历史记录，跳过发送配置: agentId={}", agentId);
                    sessionConfig = SessionConfig.getDefaultInstance();
                } else {
                    // 首次调用，发送完整配置
                    log.info("✨ 首次调用，发送完整配置: agentId={}", agentId);
                    sessionConfig = ConfigProtoConverter.toSessionConfig(config);
                }

                // 7.0 打印 MCP 服务器配置（调试用）
                log.info("[DEBUG] MCP Servers 配置: count={}, servers={}",
                    sessionConfig.getMcpServersMap().size(),
                    sessionConfig.getMcpServersMap().keySet());

                // 7.05 打印提示词配置（调试用）
                log.info("[DEBUG] 提示词配置: baseInstructions长度={}, developerInstructions长度={}, configChanged={}",
                    (sessionConfig.getBaseInstructions() != null ? sessionConfig.getBaseInstructions().length() : 0),
                    (sessionConfig.getDeveloperInstructions() != null ? sessionConfig.getDeveloperInstructions().length() : 0),
                    configChanged);
                if (sessionConfig.getDeveloperInstructions() != null && sessionConfig.getDeveloperInstructions().length() > 0) {
                    log.info("[DEBUG] developerInstructions内容前200字符: {}",
                        sessionConfig.getDeveloperInstructions().substring(0, Math.min(200, sessionConfig.getDeveloperInstructions().length())));
                }

                // 7.1 关键字段埋点，方便对比云端与本地
                ModelProviderInfo prov = sessionConfig.hasProviderInfo() ? sessionConfig.getProviderInfo() : null;
                log.info("[DEBUG] Codex 请求参数校验: model={}, provider={}, wireApi={}, baseUrl={}, approvalPolicy={}, sandboxPolicy={}, baseInstructions={}, developerInstructions={}, promptLen={}, historyBytes={}",
                    sessionConfig.getModel(),
                    sessionConfig.getModelProvider(),
                    (prov != null ? prov.getWireApi().name() : ""),
                    (prov != null ? prov.getBaseUrl() : ""),
                    sessionConfig.getApprovalPolicy().name(),
                    sessionConfig.getSandboxPolicy().name(),
                    sessionConfig.getBaseInstructions(),
                    sessionConfig.getDeveloperInstructions(),
                    (context.userMessage() != null ? context.userMessage().length() : 0),
                    historyRollout.length);

                RunTaskRequest requestParams = RunTaskRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setSessionId(agent.getConversationId())
                    .setPrompt(context.userMessage())
                    .setSessionConfig(sessionConfig)
                    .setHistoryRollout(ByteString.copyFrom(historyRollout))
                    .build();

            // 8. 用于收集完整响应
            final StringBuilder fullResponseBuilder = new StringBuilder();
            final String finalAgentId = agentId;

            // 9. 调用 Codex-Agent
            log.info("即将调用 codexAgentClient.runTask(), conversationId={}", agent.getConversationId());
            codexAgentClient.runTask(
                    agent.getConversationId(),
                    requestParams,
                    new StreamObserver<>() {
                        @Override
                        public void onNext(codex.agent.RunTaskResponse proto) {
                            log.info("收到 Codex 响应: eventCase={}", proto.getEventCase());
                            try {
                                // 转换为内部事件
                                InternalCodexEvent event = InternalCodexEventConverter.toInternalEvent(proto);
                                if (event == null) {
                                    log.warn("转换后事件为 null, eventCase={}", proto.getEventCase());
                                    return;
                                }
                                // ✨ 设置主智能体名称
                                event.setSenderName(agent.getAgentName());

                                // ✨ 注入 sender_name 到 rawEventJson (兼容 Paper 透传模式)
                                String rawJson = event.getRawEventJson();
                                if (rawJson != null) {
                                    try {
                                        JsonNode node = objectMapper.readTree(rawJson);
                                        if (node.isObject()) {
                                            ((ObjectNode) node).put("sender_name", agent.getAgentName());
                                            String newJson = objectMapper.writeValueAsString(node);
                                            event.setRawEventJson(newJson);
                                        }
                                    } catch (Exception e) {
                                        log.warn("无法注入 sender_name 到主智能体事件: {}", e.getMessage());
                                    }
                                }

                                log.info("转换后事件: status={}, eventType={}", event.getStatus(), event.getEventType());

                                // 1. 实时持久化完整事件包（Message, ToolCall, Reasoning等）
                                persistCompleteEvent(context.conversationId(), agent.getAgentName(), event);

                                // 2. 收集文本响应（仅用于更新 Agent 状态，不负责持久化历史）
                                collectTextResponse(event, fullResponseBuilder);

                                // 3. 处理完成事件（仅持久化 Rollout 状态）
                                if (event.getStatus() == InternalCodexEvent.Status.FINISHED) {
                                    handleFinished(event, agent, finalAgentId, context.conversationId(), fullResponseBuilder);
                                }

                                // 4. 回调给调用方（前端展示）
                                eventConsumer.accept(event);
                            } catch (Exception e) {
                                log.error("处理 Codex 事件失败", e);
                                onError.accept(e);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            // 只在主任务时注销流
                            if (!context.isSubTask() && !isCalledFromCallAgentTool()) {
                                sessionStreamRegistry.unregister(context.conversationId());
                            }
                            log.error("Codex 流错误回调触发: {}", traceInfo, t);
                            onError.accept(t);
                        }

                        @Override
                        public void onCompleted() {
                            // 只在主任务时注销流
                            if (!context.isSubTask() && !isCalledFromCallAgentTool()) {
                                sessionStreamRegistry.unregister(context.conversationId());
                            }
                            log.info("Codex 流完成回调触发: {}", traceInfo);
                            onCompleted.run();
                        }
                    }
            );
            log.info("codexAgentClient.runTask() 调用已发起（异步）, conversationId={}", agent.getConversationId());

        } catch (Exception e) {
            // 只在主任务时注销流
            if (!context.isSubTask() && !isCalledFromCallAgentTool()) {
                sessionStreamRegistry.unregister(context.conversationId());
            }
            log.error("执行任务失败: {}", traceInfo, e);
            onError.accept(e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 解析目标 Agent ID
     */
    private String resolveAgentId(ExecutionContextExtended context) {
        String agentId = context.agentId();

        if (agentId == null || agentId.isEmpty()) {
            if (context.conversationId() == null || context.conversationId().isEmpty()) {
                throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM, "agentId 和 conversationId 不能同时为空");
            }
            AgentEntity primaryAgent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, context.conversationId())
                            .eq(AgentEntity::getIsPrimary, true)
            );
            if (primaryAgent == null) {
                throw new AgentOzException(AgentOzErrorCode.PRIMARY_AGENT_MISSING, context.conversationId());
            }
            agentId = primaryAgent.getAgentId();
            log.info("自动路由至主智能体: agentId={}", agentId);
        }

        return agentId;
    }

    /**
     * 加载 Agent
     */
    private AgentEntity loadAgent(String agentId) {
        AgentEntity agent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
        );
        if (agent == null) {
            throw new AgentOzException(AgentOzErrorCode.AGENT_NOT_FOUND, agentId);
        }
        return agent;
    }

    /**
     * 加载配置
     */
    private AgentConfigEntity loadConfig(String configId) {
        AgentConfigEntity config = agentConfigRepository.selectOne(
                new LambdaQueryWrapper<AgentConfigEntity>()
                        .eq(AgentConfigEntity::getConfigId, configId)
        );
        if (config == null) {
            throw new AgentOzException(AgentOzErrorCode.CONFIG_NOT_FOUND, configId);
        }
        return config;
    }

    /**
     * 注入系统 MCP 并为所有业务 MCP 注入通用请求头
     * 
     * <p>职责划分：</p>
     * <ul>
     *   <li>业务方（如 Paper）：配置 MCP 的静态部分（URL、第三方认证等）</li>
     *   <li>AgentOz：注入运行时上下文（agentId, conversationId）到所有 MCP</li>
     * </ul>
     */
    private void injectSystemMcp(AgentConfigEntity config, String agentId, String conversationId) {
        try {
            String originalJson = config.getMcpConfigJson();
            
            ObjectNode rootNode;
            if (originalJson == null || originalJson.trim().isEmpty()) {
                rootNode = objectMapper.createObjectNode();
            } else {
                JsonNode node = objectMapper.readTree(originalJson);
                rootNode = node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
            }

            // 1. 确定 MCP 配置的根节点（兼容 mcp_servers 嵌套结构）
            ObjectNode mcpRoot = rootNode;
            if (rootNode.has("mcp_servers") && rootNode.get("mcp_servers").isObject()) {
                mcpRoot = (ObjectNode) rootNode.get("mcp_servers");
            }

            // 2. 为所有已有的业务 MCP 注入通用请求头
            final ObjectNode finalMcpRoot = mcpRoot;
            java.util.List<String> mcpNames = new java.util.ArrayList<>();
            mcpRoot.fieldNames().forEachRemaining(mcpNames::add);
            for (String mcpName : mcpNames) {
                JsonNode mcpConfig = finalMcpRoot.get(mcpName);
                if (mcpConfig.isObject()) {
                    ObjectNode mcpNode = (ObjectNode) mcpConfig;
                    ObjectNode headers = mcpNode.has("http_headers") && mcpNode.get("http_headers").isObject()
                            ? (ObjectNode) mcpNode.get("http_headers")
                            : objectMapper.createObjectNode();
                    
                    // 注入通用请求头（不覆盖已有的值）
                    if (!headers.has("X-Agent-ID")) {
                        headers.put("X-Agent-ID", agentId);
                    }
                    if (!headers.has("X-Conversation-ID")) {
                        headers.put("X-Conversation-ID", conversationId);
                    }
                    mcpNode.set("http_headers", headers);
                }
            }

            // 3. 注入 AgentOz 系统 MCP（用于 Agent 间协作）
            String token = jwtUtils.generateToken(agentId, conversationId);
            ObjectNode sysMcpConfig = objectMapper.createObjectNode();
            sysMcpConfig.put("server_type", "streamable_http");
            sysMcpConfig.put("url", websiteUrl + "/mcp/message");
            ObjectNode sysHeaders = objectMapper.createObjectNode();
            sysHeaders.put("Authorization", "Bearer " + token);
            sysHeaders.put("X-Agent-ID", agentId);
            sysHeaders.put("X-Conversation-ID", conversationId);
            sysMcpConfig.set("http_headers", sysHeaders);
            mcpRoot.set("agentoz_system", sysMcpConfig);
            
            config.setMcpConfigJson(objectMapper.writeValueAsString(rootNode));
            log.info("注入系统 MCP 完成: agentId={}, conversationId={}, mcpCount={}", 
                    agentId, conversationId, mcpRoot.size());
        } catch (Exception e) {
            log.error("注入系统MCP失败", e);
        }
    }

    /**
     * 从事件中收集文本响应
     */
    private void collectTextResponse(InternalCodexEvent event, StringBuilder builder) {
        if (event.getEventType() == null) return;

        // 根据事件类型提取文本
        String eventType = event.getEventType();
        String rawJson = event.getRawEventJson();

        try {
            if ("agent_message_delta".equals(eventType) && rawJson != null) {
                JsonNode node = objectMapper.readTree(rawJson);
                if (node.has("delta") && node.get("delta").has("text")) {
                    builder.append(node.get("delta").get("text").asText());
                }
            } else if ("agent_message".equals(eventType) && rawJson != null) {
                // 完整消息，替换而非追加
                JsonNode node = objectMapper.readTree(rawJson);
                if (node.has("content")) {
                    JsonNode content = node.get("content");
                    if (content.isArray()) {
                        StringBuilder text = new StringBuilder();
                        for (JsonNode item : content) {
                            if (item.has("text")) {
                                text.append(item.get("text").asText());
                            }
                        }
                        if (!text.isEmpty()) {
                            builder.setLength(0);
                            builder.append(text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析文本响应失败: {}", e.getMessage());
        }
    }

    /**
     * 实时持久化完整事件包
     */
    private void persistCompleteEvent(String conversationId, String senderName, InternalCodexEvent event) {
        String eventType = event.getEventType();
        String rawJson = event.getRawEventJson();
        if (eventType == null || rawJson == null) return;

        try {
            JsonNode node = objectMapper.readTree(rawJson);
            ObjectNode historyItem = null;

            if ("agent_message".equals(eventType)) {
                // 1. 完整的智能体回复
                historyItem = createAgentMessageItem(senderName, node);
            } else if ("item_completed".equals(eventType)) {
                // 2. 完整的工具调用（包括 CallAgent）
                historyItem = createToolCallItem(senderName, node);
            } else if ("agent_reasoning".equals(eventType)) {
                // 3. 完整的思考过程
                historyItem = createReasoningItem(senderName, node);
            }

            if (historyItem != null) {
                appendHistoryItem(conversationId, historyItem);
                log.info("[Persistence] ✓ 已实时保存事件: type={}, sender={}", eventType, senderName);
            }
        } catch (Exception e) {
            log.warn("[Persistence] ✗ 解析事件并持久化失败: type={}, error={}", eventType, e.getMessage());
        }
    }

    private ObjectNode createAgentMessageItem(String senderName, JsonNode node) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", UUID.randomUUID().toString());
        item.put("type", "AgentMessage");
        item.put("sender", senderName);
        item.put("timestamp", LocalDateTime.now().toString());
        
        // 转换内容格式
        ArrayNode content = objectMapper.createArrayNode();
        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode c : node.get("content")) {
                if (c.has("text")) {
                    ObjectNode textNode = objectMapper.createObjectNode();
                    textNode.put("type", "text");
                    textNode.put("text", c.get("text").asText());
                    content.add(textNode);
                }
            }
        }
        item.set("content", content);
        return item;
    }

    private ObjectNode createToolCallItem(String senderName, JsonNode node) {
        // 期望结构: { "item": { "type": "function_call", "name": "...", "arguments": "...", "result": "..." } }
        if (!node.has("item")) return null;
        JsonNode toolItem = node.get("item");
        
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
        item.put("type", "AgentMessage"); // 思考过程暂时也用 AgentMessage 渲染
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

    /**
     * 追加历史项到会话
     */
    private void appendHistoryItem(String conversationId, ObjectNode historyItem) {
        try {
            ConversationEntity conversation = conversationRepository.selectOne(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getConversationId, conversationId)
            );
            if (conversation == null) return;

            String currentHistory = conversation.getHistoryContext();
            if (currentHistory == null || currentHistory.isEmpty() || "null".equals(currentHistory)) {
                currentHistory = "[]";
            }

            JsonNode historyNode = objectMapper.readTree(currentHistory);
            if (historyNode.isArray()) {
                ((ArrayNode) historyNode).add(historyItem);
                conversation.setHistoryContext(objectMapper.writeValueAsString(historyNode));
                
                // 更新最后一条消息状态
                if ("AgentMessage".equals(historyItem.get("type").asText())) {
                    JsonNode contentArr = historyItem.get("content");
                    if (contentArr != null && contentArr.size() > 0) {
                        String text = contentArr.get(0).path("text").asText("");
                        conversation.setLastMessageContent(truncateText(text, 500));
                        conversation.setLastMessageType("assistant");
                    }
                }
                
                conversation.setLastMessageAt(LocalDateTime.now());
                Integer count = conversation.getMessageCount();
                conversation.setMessageCount(count != null ? count + 1 : 1);
                
                conversationRepository.updateById(conversation);
            }
        } catch (Exception e) {
            log.error("追加历史项失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 处理完成事件
     */
    private void handleFinished(
            InternalCodexEvent event,
            AgentEntity agent,
            String agentId,
            String conversationId,
            StringBuilder fullResponseBuilder
    ) {
        byte[] rollout = event.getUpdatedRollout();
        if (rollout == null || rollout.length == 0) return;

        // 更新 Agent 的 activeContext
        agent.setActiveContextFromBytes(rollout);

        // 更新 Agent 的输出状态（用于展示，不涉及历史记录）
        String finalResponse = fullResponseBuilder.toString();
        if (!finalResponse.isEmpty()) {
            agent.updateOutputState(finalResponse);
        }

        // 持久化 Agent 状态
        agentRepository.updateById(agent);
        log.info("Agent 状态已更新: agentId={}, rolloutSize={} bytes", agentId, rollout.length);
    }

    /**
     * 追加消息到会话历史 (仅用于 User 输入)
     */
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

    /**
     * 检测当前调用是否来自 CallAgentTool
     * 通过检查调用栈判断是否为子任务调用
     */
    private boolean isCalledFromCallAgentTool() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // 检查调用栈中是否有 CallAgentTool
            if (className.contains("CallAgentTool") &&
                !className.contains("AgentExecutionManager")) {
                log.debug("[AgentExecutionManager] 检测到来自 CallAgentTool 的调用");
                return true;
            }
        }
        return false;
    }
}
