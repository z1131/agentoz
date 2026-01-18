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
import com.deepknow.agentoz.infra.config.A2AConfig;
import com.deepknow.agentoz.mcp.tool.CallAgentTool;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.ConversationEntity;
import codex.agent.RunTaskRequest;
import codex.agent.SessionConfig;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final TaskStore taskStore;

    private final String websiteUrl = "https://agentoz.deepknow.online";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final Map<String, Consumer<InternalCodexEvent>> sessionStreams = new ConcurrentHashMap<>();

    // 跟踪每个会话的活跃子任务数量
    private final Map<String, AtomicInteger> activeSubTasks = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private com.deepknow.agentoz.service.RedisAgentTaskQueue redisAgentTaskQueue;

    /**
     * 检查 Agent 是否正在执行任务
     *
     * @param agentId Agent ID
     * @return true 如果 Agent 正在执行任务，false 如果空闲
     */
    public boolean isAgentBusy(String agentId) {
        // 优先使用 Redis（如果可用）
        if (redisAgentTaskQueue != null) {
            return redisAgentTaskQueue.isAgentBusy(agentId);
        }
        // 否则返回 false（降级处理，允许多任务并发）
        return false;
    }

    /**
     * 标记 Agent 开始执行任务
     *
     * @param agentId Agent ID
     * @param taskId 任务 ID
     */
    public void markAgentBusy(String agentId, String taskId) {
        if (redisAgentTaskQueue != null) {
            redisAgentTaskQueue.markAgentBusy(agentId, taskId);
        }
    }

    /**
     * 标记 Agent 完成任务（空闲）
     *
     * @param agentId Agent ID
     */
    public void markAgentFree(String agentId) {
        if (redisAgentTaskQueue != null) {
            redisAgentTaskQueue.markAgentFree(agentId);
        }
    }

    public record ExecutionContext(String agentId, String conversationId, String userMessage, String role, String senderName) {}

    public record ExecutionContextExtended(
            String agentId, String conversationId, String userMessage, String role, String senderName,
            boolean isSubTask
    ) {
        public ExecutionContextExtended(String agentId, String conversationId, String userMessage, String role, String senderName) {
            this(agentId, conversationId, userMessage, role, senderName, false);
        }
    }

    public void executeTask(ExecutionContext context, Consumer<InternalCodexEvent> eventConsumer, Runnable onCompleted, Consumer<Throwable> onError) {
        executeTaskExtended(new ExecutionContextExtended(context.agentId(), context.conversationId(), context.userMessage(), context.role(), context.senderName(), false), eventConsumer, onCompleted, onError);
    }

    public void broadcastSubTaskEvent(String conversationId, InternalCodexEvent event) {
        Consumer<InternalCodexEvent> consumer = sessionStreams.get(conversationId);

        log.info("📡 [broadcastSubTaskEvent] convId={}, eventType={}, hasConsumer={}, senderName={}",
            conversationId, event.getEventType(), consumer != null, event.getSenderName());

        if (consumer != null) {
            try {
                consumer.accept(event);
                log.info("✅ [broadcastSubTaskEvent] 事件已发送到前端: convId={}, eventType={}",
                    conversationId, event.getEventType());
            } catch (Exception e) {
                log.error("❌ [broadcastSubTaskEvent] 发送事件失败: convId={}, error={}",
                    conversationId, e.getMessage(), e);
            }
        } else {
            log.warn("⚠️  [broadcastSubTaskEvent] 找不到 SSE 连接: convId={}, 当前sessionStreams大小={}",
                conversationId, sessionStreams.size());

            // 输出所有 conversationId 帮助调试
            if (log.isDebugEnabled()) {
                log.debug("当前 sessionStreams 中的 keys: {}", sessionStreams.keySet());
            }
        }
    }

    /**
     * 增加活跃子任务计数
     * 用于异步调用场景，防止父任务完成时关闭 SSE 连接
     */
    public void incrementActiveSubTasks(String conversationId) {
        activeSubTasks.computeIfAbsent(conversationId, k -> new AtomicInteger(0)).incrementAndGet();
        log.info("➕ [incrementActiveSubTasks] convId={}, activeCount={}",
            conversationId, activeSubTasks.get(conversationId).get());
    }

    /**
     * 减少活跃子任务计数
     * 当子任务完成时调用，如果计数归零则可以关闭 SSE 连接
     */
    public void decrementActiveSubTasks(String conversationId) {
        AtomicInteger counter = activeSubTasks.get(conversationId);
        if (counter != null) {
            int newCount = counter.decrementAndGet();
            log.info("➖ [decrementActiveSubTasks] convId={}, activeCount={}",
                conversationId, newCount);

            if (newCount <= 0) {
                // 所有子任务都完成了
                activeSubTasks.remove(conversationId);
                log.info("✅ [decrementActiveSubTasks] 所有子任务完成，清理计数: convId={}", conversationId);
            }
        }
    }

    /**
     * 持久化 Codex 事件到会话历史
     * 公开方法，供 CallAgentTool 等外部调用
     */
    public void persistEvent(String conversationId, String agentId, String senderName, InternalCodexEvent event) {
        persist(conversationId, agentId, senderName, event);
    }

    /**
     * 更新 Agent 的 activeContext
     * 公开方法，供 CallAgentTool 等外部调用
     */
    public void updateAgentActiveContext(String agentId, byte[] rolloutBytes) {
        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );
            if (agent != null) {
                agent.setActiveContextFromBytes(rolloutBytes);
                agentRepository.updateById(agent);
                log.info("Agent activeContext 已更新: agentId={}, size={} bytes", agentId, rolloutBytes != null ? rolloutBytes.length : 0);
            }
        } catch (Exception e) {
            log.error("更新 Agent activeContext 失败: agentId={}", agentId, e);
        }
    }

    public void executeTaskExtended(
            ExecutionContextExtended context,
            Consumer<InternalCodexEvent> eventConsumer,
            Runnable onCompleted,
            Consumer<Throwable> onError
    ) {
        final String curTaskId = context.isSubTask ? UUID.randomUUID().toString() : context.conversationId();

        try {
            if (!context.isSubTask) sessionStreams.put(context.conversationId(), eventConsumer);

            AgentEntity agent = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, resolveAgentId(context)));
            AgentConfigEntity config = agentConfigRepository.selectOne(new LambdaQueryWrapper<AgentConfigEntity>().eq(AgentConfigEntity::getConfigId, agent.getConfigId()));

            appendMessage(context.conversationId(), context.role(), context.userMessage(), (context.senderName() != null) ? context.senderName() : "user");
            agentContextManager.onAgentCalled(agent.getAgentId(), context.userMessage(), (context.senderName() != null) ? context.senderName() : "user");

            injectMcpHeaders(config, agent.getAgentId(), agent.getConversationId(), curTaskId);

            // 标记 Agent 为忙碌（仅在非子任务时）
            if (!context.isSubTask) {
                markAgentBusy(agent.getAgentId(), curTaskId);
            }

            RunTaskRequest req = RunTaskRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString()).setSessionId(agent.getConversationId())
                    .setPrompt(context.userMessage()).setSessionConfig(ConfigProtoConverter.toSessionConfig(config))
                    .setHistoryRollout(ByteString.copyFrom(agent.getActiveContextBytes())).build();

            final StringBuilder sb = new StringBuilder();
            // 保存 StreamObserver 引用，用于 A2A 中断时关闭
            final StreamObserver<codex.agent.RunTaskResponse>[] streamRef = new StreamObserver[]{null};
            codexAgentClient.runTask(agent.getConversationId(), req, new StreamObserver<codex.agent.RunTaskResponse>() {
                private Task subTaskCandidate = null;
                private boolean isInterrupted = false;

                @Override
                public void onNext(codex.agent.RunTaskResponse p) {
                    if (isInterrupted) return;
                    try {
                        InternalCodexEvent e = InternalCodexEventConverter.toInternalEvent(p);
                        if (e == null) return;
                        e.setSenderName(agent.getAgentName());
                        e.setAgentId(agent.getAgentId());
                        persist(context.conversationId(), agent.getAgentId(), agent.getAgentName(), e);
                        collectTextRobustly(e, sb);

                        if ("item.completed".equals(e.getEventType()) && e.getRawEventJson() != null) {
                            JsonNode toolRes = objectMapper.readTree(e.getRawEventJson()).path("item").path("result");
                            for (JsonNode contentItem : toolRes.path("content")) {
                                String text = contentItem.path("text").asText("");
                                if (text.contains("\"id\"") && text.contains("\"status\"")) {
                                    try {
                                        Task t = objectMapper.readValue(text, Task.class);
                                        if (t.getId() != null && (t.getStatus().state() == TaskState.SUBMITTED || t.getStatus().state() == TaskState.WORKING)) {
                                            subTaskCandidate = t;
                                            isInterrupted = true;
                                            // 关闭 StreamObserver，触发 Codex EOF
                                            if (streamRef[0] != null) {
                                                log.info("[A2A] 关闭 StreamObserver 触发 Codex 中断: conversationId={}", context.conversationId());
                                                streamRef[0].onCompleted();
                                            }
                                            suspendAndRegister(t.getId(), agent, config, eventConsumer);
                                            throw new RuntimeException("A2A_INTERRUPT");
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }

                        if (e.getStatus() == InternalCodexEvent.Status.FINISHED) {
                            // 检查是否是中间状态（updated_rollout_interrupt）
                            if (e.isIntermediateRollout()) {
                                log.info("[A2A] 收到中间状态，持久化但不关闭流: conversationId={}", context.conversationId());
                                if (e.getUpdatedRollout() != null && e.getUpdatedRollout().length > 0) {
                                    agent.setActiveContextFromBytes(e.getUpdatedRollout());
                                    agentRepository.updateById(agent);
                                    log.info("[A2A] 中间状态已持久化: agentId={}, size={} bytes", agent.getAgentId(), e.getUpdatedRollout().length);
                                }
                                // 不调用 eventConsumer.accept(e)，因为流会保持打开
                                // 等待子任务完成后恢复
                                return;
                            }

                            // 正常完成
                            log.info("🔍 [onNext-FINISHED] 检查 updatedRollout: hasRollout={}, size={}",
                                e.getUpdatedRollout() != null,
                                e.getUpdatedRollout() != null ? e.getUpdatedRollout().length : 0);

                            if (e.getUpdatedRollout() != null && e.getUpdatedRollout().length > 0) {
                                agent.setActiveContextFromBytes(e.getUpdatedRollout());
                                agentRepository.updateById(agent);
                                log.info("✅ [FINISHED] 已持久化 updatedRollout: agentId={}, size={} bytes",
                                    agent.getAgentId(), e.getUpdatedRollout().length);
                            } else {
                                log.warn("⚠️  [FINISHED] updatedRollout 为空！agentId={}", agent.getAgentId());
                            }

                            // 替换原有的 updateOutputState，使用 ContextManager 统一管理状态 (设置为 IDLE)
                            agentContextManager.onAgentResponse(agent.getAgentId(), sb.toString());
                        } else {
                            // 处理过程中的事件更新 (Thinking, Call Tool...)
                            agentContextManager.onCodexEvent(agent.getAgentId(), e);
                        }
                        
                        eventConsumer.accept(e);
                    } catch (RuntimeException ex) {
                        if (!"A2A_INTERRUPT".equals(ex.getMessage())) {
                            log.error("Next fail", ex);
                        }
                    } catch (Exception ex) {
                        log.error("Next fail", ex);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (!isInterrupted) {
                        if (!context.isSubTask) {
                            // 检查是否还有活跃的子任务
                            AtomicInteger subTaskCount = activeSubTasks.get(context.conversationId());
                            if (subTaskCount != null && subTaskCount.get() > 0) {
                                log.info("⏳ [onError] 父任务错误，但还有 {} 个子任务活跃，延迟关闭 SSE: convId={}",
                                    subTaskCount.get(), context.conversationId());
                                markAgentFree(context.agentId());
                                onError.accept(t);
                                return;
                            }

                            // 没有活跃子任务，正常关闭
                            sessionStreams.remove(context.conversationId());
                            activeSubTasks.remove(context.conversationId());
                            // 标记 Agent 为空闲
                            markAgentFree(context.agentId());
                            onError.accept(t);
                        }
                    } else {
                        log.info("[A2A] StreamObserver 错误 (预期行为): conversationId={}", context.conversationId());
                    }
                }

                @Override
                public void onCompleted() {
                    if (!isInterrupted) {
                        // 只在非子任务时处理
                        if (!context.isSubTask) {
                            // 检查是否还有活跃的子任务
                            AtomicInteger subTaskCount = activeSubTasks.get(context.conversationId());
                            if (subTaskCount != null && subTaskCount.get() > 0) {
                                log.info("⏳ [onCompleted] 父任务完成，但还有 {} 个子任务活跃，延迟关闭 SSE: convId={}",
                                    subTaskCount.get(), context.conversationId());
                                // 不立即关闭 SSE，等所有子任务完成
                                // 标记 Agent 为空闲（允许父任务完成）
                                markAgentFree(context.agentId());
                                onCompleted.run();
                                return;
                            }

                            // 没有活跃子任务，正常关闭
                            log.info("✅ [onCompleted] 父任务完成，无活跃子任务，关闭 SSE: convId={}",
                                context.conversationId());
                            sessionStreams.remove(context.conversationId());
                            activeSubTasks.remove(context.conversationId());
                            // 标记 Agent 为空闲
                            markAgentFree(context.agentId());
                            onCompleted.run();
                        }
                    } else {
                        log.info("[A2A] StreamObserver 完成 (预期行为): conversationId={}", context.conversationId());
                    }
                }

                {
                    streamRef[0] = this;
                }
            });
        } catch (Exception e) { log.error("Execution error", e); onError.accept(e); }
    }

    private String extractResult(Task task) {
        if (task.getArtifacts() == null) return "No result";
        StringBuilder res = new StringBuilder();
        for (Artifact art : task.getArtifacts()) {
            if (art.parts() != null) for (Part<?> p : art.parts()) if (p instanceof TextPart) res.append(((TextPart) p).getText());
        }
        return res.toString();
    }

    private void collectTextRobustly(InternalCodexEvent event, StringBuilder accumulator) {
        try {
            String json = event.getRawEventJson();
            if (json == null) return;
            JsonNode node = objectMapper.readTree(json);
            String itemText = node.path("item").path("text").asText("");
            if (!itemText.isEmpty() && accumulator.indexOf(itemText) == -1) { accumulator.append(itemText); return; }
            String deltaText = node.path("delta").path("text").asText("");
            if (!deltaText.isEmpty()) { accumulator.append(deltaText); return; }
            JsonNode content = node.path("content");
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) if (part.has("text")) sb.append(part.get("text").asText());
                if (sb.length() > accumulator.length()) { accumulator.setLength(0); accumulator.append(sb); }
            }
        } catch (Exception ignored) {}
    }

    private String resolveAgentId(ExecutionContextExtended c) {
        if (c.agentId() != null && !c.agentId().isEmpty()) return c.agentId();
        AgentEntity p = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, c.conversationId()).eq(AgentEntity::getIsPrimary, true));
        if (p == null) throw new AgentOzException(AgentOzErrorCode.PRIMARY_AGENT_MISSING, c.conversationId());
        return p.getAgentId();
    }

    private void injectMcpHeaders(AgentConfigEntity cfg, String aid, String cid, String tid) {
        try {
            ObjectNode r = (cfg.getMcpConfigJson() == null || cfg.getMcpConfigJson().isEmpty()) ? objectMapper.createObjectNode() : (ObjectNode) objectMapper.readTree(cfg.getMcpConfigJson());
            ObjectNode m = r.has("mcp_servers") ? (ObjectNode) r.get("mcp_servers") : r;
            m.fieldNames().forEachRemaining(n -> {
                JsonNode j = m.get(n);
                if (j.isObject()) {
                    ObjectNode h = j.has("http_headers") ? (ObjectNode) j.get("http_headers") : objectMapper.createObjectNode();
                    h.put("X-Agent-ID", aid); h.put("X-Conversation-ID", cid);
                    h.put("X-A2A-Parent-Task-ID", tid);
                    ((ObjectNode) j).set("http_headers", h);
                }
            });
            String tk = jwtUtils.generateToken(aid, cid);
            ObjectNode sys = objectMapper.createObjectNode(); sys.put("server_type", "streamable_http"); sys.put("url", websiteUrl + "/mcp/message");
            ObjectNode sh = objectMapper.createObjectNode(); sh.put("Authorization", "Bearer " + tk); sh.put("X-Agent-ID", aid); sh.put("X-Conversation-ID", cid);
            sys.set("http_headers", sh); m.set("agentoz_system", sys);
            cfg.setMcpConfigJson(objectMapper.writeValueAsString(r));
        } catch (Exception ignored) {}
    }

    private void persist(String conversationId, String agentId, String senderName, InternalCodexEvent event) {
        try {
            if (event.getEventType() == null || event.getRawEventJson() == null) return;
            JsonNode n = objectMapper.readTree(event.getRawEventJson());
            ObjectNode item = null;
            if ("agent_message".equals(event.getEventType())) item = createAgentMsg(senderName, n);
            else if ("item.completed".equals(event.getEventType())) item = createToolItem(senderName, n);
            else if ("agent_reasoning".equals(event.getEventType())) item = createReasoningItem(senderName, n);
            else item = createGenericItem(senderName, n, event.getEventType());
            if (item != null) {
                appendHistoryItem(conversationId, item);
                appendAgentHistory(agentId, item);
                if (event.getDisplayItems() == null) event.setDisplayItems(new java.util.ArrayList<>());
                event.getDisplayItems().add(objectMapper.writeValueAsString(item));
            }
        } catch (Exception ignored) {}
    }

    private void appendAgentHistory(String agentId, ObjectNode item) {
        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
            );
            if (agent == null) return;
            ArrayNode h = (agent.getFullHistory() == null || agent.getFullHistory().isEmpty() || "null".equals(agent.getFullHistory())) ? objectMapper.createArrayNode() : (ArrayNode) objectMapper.readTree(agent.getFullHistory());
            h.add(item);
            agent.setFullHistory(objectMapper.writeValueAsString(h));
            agentRepository.updateById(agent);
            log.debug("Agent fullHistory 已更新: agentId={}, items={}", agentId, h.size());
        } catch (Exception e) {
            log.warn("更新 Agent fullHistory 失败: agentId={}", agentId, e);
        }
    }

    private ObjectNode createAgentMsg(String senderName, JsonNode n) {
        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", n);
        item.put("type", "agent_message");
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private ObjectNode createToolItem(String senderName, JsonNode n) {
        JsonNode itemNode = n.path("item");
        if (itemNode.isMissingNode()) return null;
        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", itemNode);
        item.put("type", "item.completed");
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private ObjectNode createReasoningItem(String senderName, JsonNode n) {
        ObjectNode item = objectMapper.createObjectNode();
        ObjectNode reasoningContent = objectMapper.createObjectNode();
        reasoningContent.put("type", "text");
        reasoningContent.put("text", n.path("content").asText(""));
        item.set("item", reasoningContent);
        item.put("type", "agent_reasoning");
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private ObjectNode createGenericItem(String senderName, JsonNode n, String eventType) {
        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", n);
        item.put("type", eventType);
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private void appendHistoryItem(String cid, ObjectNode i) {
        try {
            ConversationEntity c = conversationRepository.selectOne(new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getConversationId, cid));
            if (c == null) return;
            ArrayNode h = (c.getHistoryContext() == null || c.getHistoryContext().isEmpty() || "null".equals(c.getHistoryContext())) ? objectMapper.createArrayNode() : (ArrayNode) objectMapper.readTree(c.getHistoryContext());
            h.add(i); c.setHistoryContext(objectMapper.writeValueAsString(h));
            if ("agent_message".equals(i.get("type").asText())) {
                String text = i.path("item").path("text").asText("");
                c.setLastMessageContent(trunc(text, 500)); c.setLastMessageType("assistant");
            }
            c.setLastMessageAt(LocalDateTime.now()); c.setMessageCount((c.getMessageCount() != null ? c.getMessageCount() : 0) + 1);
            conversationRepository.updateById(c);
        } catch (Exception ignored) {}
    }

    private void appendMessage(String cid, String role, String contentText, String senderName) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "text");
        content.put("text", contentText);

        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", content);
        item.put("type", "agent_message");
        item.put("agentId", "");
        item.put("agentName", senderName);

        appendHistoryItem(cid, item);
    }

    private String trunc(String t, int m) { if (t == null) return null; return t.length() <= m ? t : t.substring(0, m) + "..."; }

    /**
     * A2A 挂起：注册终端监听器并等待子任务完成
     */
    private void suspendAndRegister(String taskId, AgentEntity agent, AgentConfigEntity config, Consumer<InternalCodexEvent> eventConsumer) {
        if (taskStore instanceof A2AConfig.A2AObservableStore store) {
            store.addTerminalListener(taskId, (finished) -> {
                String result = extractResult(finished);
                log.info("[A2A] 委派任务完成，恢复 Agent: conversationId={}, taskId={}", agent.getConversationId(), taskId);

                // 发送结果事件
                InternalCodexEvent resultEvent = createResultEvent(result, agent.getConversationId(), agent.getAgentId());
                eventConsumer.accept(resultEvent);

                // 恢复 Agent A 的执行
                resumeAgentAfterA2A(result, agent, config, eventConsumer);
            });
        }
    }

    /**
     * A2A 恢复：子任务完成后，重新调用 Codex 继续执行
     */
    private void resumeAgentAfterA2A(String result, AgentEntity agent, AgentConfigEntity config, Consumer<InternalCodexEvent> eventConsumer) {
        try {
            // 构建恢复提示
            String resumePrompt = String.format(
                    "委派任务执行完毕，结果如下：\n%s\n\n请继续之前的任务。",
                    result
            );

            log.info("[A2A] 恢复 Agent 执行: agentId={}, conversationId={}", agent.getAgentId(), agent.getConversationId());

            // 重新调用 Codex，使用最新的 activeContext
            RunTaskRequest req = RunTaskRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setSessionId(agent.getConversationId())
                    .setPrompt(resumePrompt)
                    .setSessionConfig(ConfigProtoConverter.toSessionConfig(config))
                    .setHistoryRollout(ByteString.copyFrom(agent.getActiveContextBytes()))
                    .build();

            codexAgentClient.runTask(agent.getConversationId(), req, new StreamObserver<codex.agent.RunTaskResponse>() {
                @Override
                public void onNext(codex.agent.RunTaskResponse p) {
                    try {
                        InternalCodexEvent e = InternalCodexEventConverter.toInternalEvent(p);
                        if (e == null) return;
                        e.setSenderName(agent.getAgentName());
                        e.setAgentId(agent.getAgentId());
                        persist(agent.getConversationId(), agent.getAgentId(), agent.getAgentName(), e);

                        if (e.getStatus() == InternalCodexEvent.Status.FINISHED) {
                            if (e.getUpdatedRollout() != null && e.getUpdatedRollout().length > 0) agent.setActiveContextFromBytes(e.getUpdatedRollout());
                            // 恢复任务完成，重置为 IDLE
                            agentContextManager.onAgentResponse(agent.getAgentId(), ""); 
                        } else {
                            agentContextManager.onCodexEvent(agent.getAgentId(), e);
                        }
                        eventConsumer.accept(e);
                    } catch (Exception ex) {
                        log.error("[A2A Resume] Next fail", ex);
                        eventConsumer.accept(InternalCodexEvent.error("恢复失败: " + ex.getMessage()));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("[A2A Resume] 任务失败: agentId={}", agent.getAgentId(), t);
                    eventConsumer.accept(InternalCodexEvent.error("恢复失败: " + t.getMessage()));
                }

                @Override
                public void onCompleted() {
                    log.info("[A2A Resume] 任务恢复完成: agentId={}", agent.getAgentId());
                    sessionStreams.remove(agent.getConversationId());
                }
            });

        } catch (Exception e) {
            log.error("[A2A] 恢复 Agent 失败: agentId={}", agent.getAgentId(), e);
            eventConsumer.accept(InternalCodexEvent.error("恢复失败: " + e.getMessage()));
        }
    }

    private InternalCodexEvent createResultEvent(String result, String conversationId, String agentId) {
        String escapedResult = result.replace("\"", "\\\"").replace("\n", "\\n");
        String rawJson = String.format(
            "{\"type\":\"a2a_delegation_completed\",\"conversationId\":\"%s\",\"content\":{\"text\":\"%s\"}}",
            conversationId, escapedResult
        );
        return InternalCodexEvent.processing("a2a_delegation_completed", rawJson)
                .setSenderName("System(A2A)")
                .setAgentId(agentId);
    }
}