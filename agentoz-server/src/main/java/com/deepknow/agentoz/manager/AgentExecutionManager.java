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
import com.deepknow.agentoz.infra.config.A2AConfig;
import com.deepknow.agentoz.mcp.tool.CallAgentTool;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.ConversationEntity;
import codex.agent.RunTaskRequest;
import codex.agent.SessionConfig;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.Artifact;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
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
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final io.a2a.server.tasks.TaskStore taskStore;

    private final String websiteUrl = "https://agentoz.deepknow.online";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final Map<String, Consumer<InternalCodexEvent>> sessionStreams = new ConcurrentHashMap<>();

    public record ExecutionContext(String agentId, String conversationId, String userMessage, String role, String senderName) {}

    public record ExecutionContextExtended(
            String agentId, String conversationId, String userMessage, String role, String senderName,
            boolean isSubTask, A2AContext a2aContext
    ) {
        public ExecutionContextExtended(String agentId, String conversationId, String userMessage, String role, String senderName) {
            this(agentId, conversationId, userMessage, role, senderName, false, null);
        }
    }

    public void executeTask(ExecutionContext context, Consumer<InternalCodexEvent> eventConsumer, Runnable onCompleted, Consumer<Throwable> onError) {
        executeTaskExtended(new ExecutionContextExtended(context.agentId(), context.conversationId(), context.userMessage(), context.role(), context.senderName(), false, null), eventConsumer, onCompleted, onError);
    }

    public void broadcastSubTaskEvent(String conversationId, InternalCodexEvent event) {
        Consumer<InternalCodexEvent> consumer = sessionStreams.get(conversationId);
        if (consumer != null) consumer.accept(event);
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

            RunTaskRequest req = RunTaskRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString()).setSessionId(agent.getConversationId())
                    .setPrompt(context.userMessage()).setSessionConfig(ConfigProtoConverter.toSessionConfig(config))
                    .setHistoryRollout(ByteString.copyFrom(agent.getActiveContextBytes())).build();

            final StringBuilder sb = new StringBuilder();
            
            // ⭐ 定义一个变量来持有本次调用的 Stream，以便可以取消它
            // 由于 Java 闭包限制，我们需要一个包装器或者直接调用 codexAgentClient
            // 这里我们依赖 onError 回调来通知 Codex 断开
            
            codexAgentClient.runTask(agent.getConversationId(), req, new StreamObserver<codex.agent.RunTaskResponse>() {
                private Task subTaskCandidate = null;
                private boolean isInterrupted = false;

                @Override
                public void onNext(codex.agent.RunTaskResponse p) {
                    if (isInterrupted) return; // 如果已中断，忽略后续事件

                    try {
                        InternalCodexEvent e = InternalCodexEventConverter.toInternalEvent(p);
                        if (e == null) return;
                        e.setSenderName(agent.getAgentName());
                        persist(context.conversationId(), agent.getAgentName(), e);
                        collect(e, sb);
                        
                        // ⭐ 核心逻辑：A2A 委派检测与中断
                        if ("item.completed".equals(e.getEventType()) && e.getRawEventJson() != null) {
                            JsonNode toolRes = objectMapper.readTree(e.getRawEventJson()).path("item").path("result");
                            for (JsonNode contentItem : toolRes.path("content")) {
                                String text = contentItem.path("text").asText("");
                                if (text.contains("\"id\"") && text.contains("\"status\"")) {
                                    try {
                                        Task t = objectMapper.readValue(text, Task.class);
                                        // 检查状态是否为 SUBMITTED 或 WORKING
                                        if (t.getId() != null && 
                                           (t.getStatus().getState() == TaskState.SUBMITTED || t.getStatus().getState() == TaskState.WORKING)) {
                                            
                                            subTaskCandidate = t;
                                            log.info("[A2A] 🛑 DELEGATION DETECTED: {}. INTERRUPTING CODEX STREAM.", t.getId());
                                            
                                            // 1. 立即中断 Codex 流 (模拟 Cancel)
                                            isInterrupted = true;
                                            // 注意：这里无法直接 cancel gRPC call，但我们可以抛出异常让 Client 关闭连接
                                            // 或者直接在这里进入挂起逻辑，不再给 Codex 回任何东西
                                            
                                            // 2. 进入挂起状态
                                            suspendAndRegisterCallback(t.getId());
                                            
                                            // 3. 提前结束本轮处理 (不发 onCompleted，让流自然断开或超时)
                                            // 如果 Codex 侧支持 Cancellation，最好显式 Cancel
                                            throw new RuntimeException("A2A_INTERRUPT"); 
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }

                        if (e.getStatus() == InternalCodexEvent.Status.FINISHED) {
                            if (e.getUpdatedRollout() != null && e.getUpdatedRollout().length > 0) agent.setActiveContextFromBytes(e.getUpdatedRollout());
                            if (sb.length() > 0) agent.updateOutputState(sb.toString());
                            agentRepository.updateById(agent);
                        }
                        eventConsumer.accept(e);
                    } catch (RuntimeException ex) {
                        if ("A2A_INTERRUPT".equals(ex.getMessage())) {
                            // 这是一个预期的中断，不需要 log error
                            log.info("[A2A] Stream interrupted for delegation.");
                        } else {
                            log.error("Next fail", ex);
                            onError.accept(ex);
                        }
                    } catch (Exception ex) { log.error("Next fail", ex); onError.accept(ex); }
                }

                @Override
                public void onError(Throwable t) { 
                    if (isInterrupted) return; // 忽略中断后的报错
                    if (!context.isSubTask) sessionStreams.remove(context.conversationId()); 
                    onError.accept(t); 
                }

                @Override
                public void onCompleted() {
                    if (isInterrupted) return; // 中断后不处理完成
                    if (!context.isSubTask) sessionStreams.remove(context.conversationId());
                    onCompleted.run();
                }

                // 挂起与唤醒逻辑
                private void suspendAndRegisterCallback(String taskId) {
                    if (taskStore instanceof A2AConfig.A2AObservableStore store) {
                        log.info("[A2A] Session Suspended, waiting for task: {}", taskId);
                        store.addTerminalListener(taskId, (finished) -> {
                            String result = extractResult(finished);
                            log.info("[A2A] 🔔 AWAKENING parent agent with result from: {}", taskId);
                            
                            // 构造唤醒消息：告诉主智能体刚才的任务完成了
                            String wakeUpPrompt = String.format(
                                "系统通知：刚才委派的任务（ID: %s）已执行完毕。\n执行结果如下：\n%s\n\n请根据此结果继续你的工作。",
                                taskId, result
                            );
                            
                            // 发起新的 Turn (A2A Re-entry)
                            executeTaskExtended(new ExecutionContextExtended(
                                    context.agentId(), 
                                    context.conversationId(), 
                                    wakeUpPrompt, 
                                    "user", // 以 User 身份注入，符合 Chat 逻辑
                                    "System(A2A)", 
                                    false, 
                                    null
                            ), eventConsumer, onCompleted, onError);
                        });
                    } else {
                        log.error("[A2A] TaskStore is not observable! Awakening failed.");
                    }
                }
            });
        } catch (Exception e) { log.error("Execution error", e); onError.accept(e); }
    }

    private String extractResult(Task task) {
        if (task.getArtifacts() == null) return "No result";
        StringBuilder sb = new StringBuilder();
        for (Artifact art : task.getArtifacts()) {
            if (art.parts() != null) {
                for (Part<?> p : art.parts()) if (p instanceof TextPart) sb.append(((TextPart) p).getText());
            }
        }
        return sb.toString();
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

    private void collect(InternalCodexEvent e, StringBuilder b) {
        try {
            if (e.getRawEventJson() == null) return;
            JsonNode n = objectMapper.readTree(e.getRawEventJson());
            if ("agent_message_delta".equals(e.getEventType())) { if (n.path("delta").has("text")) b.append(n.path("delta").path("text").asText()); }
            else if ("agent_message".equals(e.getEventType())) {
                JsonNode c = n.path("content");
                if (c.isArray()) { b.setLength(0); for (JsonNode i : c) if (i.has("text")) b.append(i.get("text").asText()); }
            }
        } catch (Exception ignored) {}
    }

    private void persist(String cid, String sdr, InternalCodexEvent ev) {
        try {
            if (ev.getEventType() == null || ev.getRawEventJson() == null) return;
            JsonNode n = objectMapper.readTree(ev.getRawEventJson());
            ObjectNode item = null;
            if ("agent_message".equals(ev.getEventType())) item = msg(sdr, n);
            else if ("item.completed".equals(ev.getEventType())) item = tool(sdr, n); // ⭐ 兼容点号
            else if ("agent_reasoning".equals(ev.getEventType())) item = reason(sdr, n);
            if (item != null) {
                appendH(cid, item);
                if (ev.getDisplayItems() == null) ev.setDisplayItems(new java.util.ArrayList<>());
                ev.getDisplayItems().add(objectMapper.writeValueAsString(item));
            }
        } catch (Exception ignored) {}
    }

    private ObjectNode msg(String s, JsonNode n) {
        ObjectNode i = objectMapper.createObjectNode(); i.put("id", UUID.randomUUID().toString()); i.put("type", "AgentMessage"); i.put("sender", s); i.put("timestamp", LocalDateTime.now().toString());
        ArrayNode c = objectMapper.createArrayNode();
        for (JsonNode x : n.path("content")) { if (x.has("text")) { ObjectNode t = objectMapper.createObjectNode(); t.put("type", "text"); t.put("text", x.get("text").asText()); c.add(t); } }
        i.set("content", c); return i;
    }

    private ObjectNode tool(String s, JsonNode n) {
        JsonNode t = n.path("item"); if (t.isMissingNode()) return null;
        ObjectNode i = objectMapper.createObjectNode(); i.put("id", UUID.randomUUID().toString()); i.put("type", "McpToolCall"); i.put("sender", s); i.put("timestamp", LocalDateTime.now().toString());
        i.put("tool", t.path("name").asText("unknown")); i.set("arguments", t.path("arguments")); i.set("result", t.path("result")); return i;
    }

    private ObjectNode reason(String s, JsonNode n) {
        ObjectNode i = objectMapper.createObjectNode(); i.put("id", UUID.randomUUID().toString()); i.put("type", "AgentMessage"); i.put("sender", s); i.put("timestamp", LocalDateTime.now().toString());
        ArrayNode c = objectMapper.createArrayNode(); ObjectNode t = objectMapper.createObjectNode(); t.put("type", "text"); t.put("text", "> [Thinking] " + n.path("content").asText("")); c.add(t); i.set("content", c); return i;
    }

    private void appendH(String cid, ObjectNode i) {
        try {
            ConversationEntity c = conversationRepository.selectOne(new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getConversationId, cid));
            if (c == null) return;
            ArrayNode h = (c.getHistoryContext() == null || c.getHistoryContext().isEmpty() || "null".equals(c.getHistoryContext())) ? objectMapper.createArrayNode() : (ArrayNode) objectMapper.readTree(c.getHistoryContext());
            h.add(i); c.setHistoryContext(objectMapper.writeValueAsString(h));
            if ("AgentMessage".equals(i.get("type").asText())) { c.setLastMessageContent(trunc(i.path("content").path(0).path("text").asText(""), 500)); c.setLastMessageType("assistant"); }
            c.setLastMessageAt(LocalDateTime.now()); c.setMessageCount((c.getMessageCount() != null ? c.getMessageCount() : 0) + 1);
            conversationRepository.updateById(c);
        } catch (Exception ignored) {}
    }

    private void appendMessage(String cid, String r, String ct, String s) {
        ObjectNode i = objectMapper.createObjectNode(); i.put("id", UUID.randomUUID().toString()); i.put("type", "assistant".equals(r) ? "AgentMessage" : "UserMessage"); i.put("sender", s); i.put("timestamp", LocalDateTime.now().toString());
        ArrayNode a = objectMapper.createArrayNode(); ObjectNode t = objectMapper.createObjectNode(); t.put("type", "text"); t.put("text", ct); a.add(t); i.set("content", a); appendH(cid, i);
    }

    private String trunc(String t, int m) { if (t == null) return null; return t.length() <= m ? t : t.substring(0, m) + "..."; }
}