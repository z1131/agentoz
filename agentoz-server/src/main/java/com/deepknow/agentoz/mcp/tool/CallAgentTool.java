package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.*;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Collections;

@Slf4j
@Component
public class CallAgentTool {

    @Autowired
    private AgentExecutionManager agentExecutionManager;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private TaskStore taskStore;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static final String A2A_DELEGATED_MARKER = "[A2A_TASK_DELEGATED:";

    @AgentTool(name = "call_agent", description = "异步委派任务")
    public String callAgent(
            McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标智能体") String targetAgentName,
            @AgentParam(name = "task", value = "指令") String task
    ) {
        String subId = UUID.randomUUID().toString();
        try {
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            AgentEntity target = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId).eq(AgentEntity::getAgentName, targetAgentName));
            if (target == null) return "Error: Target not found";

            final StringBuilder res = new StringBuilder();
            agentExecutionManager.executeTaskExtended(new AgentExecutionManager.ExecutionContextExtended(
                    target.getAgentId(), conversationId, task, "assistant", "System", true),
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        agentExecutionManager.broadcastSubTaskEvent(conversationId, event);
                        agentExecutionManager.persistEvent(conversationId, targetAgentName, event);
                        collectText(event, res);
                        if (event.getStatus() == InternalCodexEvent.Status.FINISHED
                                && event.getUpdatedRollout() != null
                                && event.getUpdatedRollout().length > 0) {
                            agentExecutionManager.updateAgentActiveContext(target.getAgentId(), event.getUpdatedRollout());
                        }
                    },
                    () -> {
                        Artifact art = new Artifact(UUID.randomUUID().toString(), "Result", null, List.of(new TextPart(res.toString())), Collections.emptyMap(), Collections.emptyList());
                        Task completed = new Task(subId, conversationId, new TaskStatus(TaskState.COMPLETED, null, OffsetDateTime.now()), List.of(art), Collections.emptyList(), Collections.emptyMap());
                        taskStore.save(completed);
                    },
                    (Throwable t) -> {
                        Task failed = new Task(subId, conversationId, new TaskStatus(TaskState.FAILED, null, OffsetDateTime.now()), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
                        taskStore.save(failed);
                    });

            Task initial = new Task(subId, conversationId, new TaskStatus(TaskState.SUBMITTED, null, OffsetDateTime.now()), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
            return objectMapper.writeValueAsString(initial);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private void collectText(InternalCodexEvent event, StringBuilder builder) {
        try {
            String json = event.getRawEventJson();
            if (json == null) return;
            JsonNode node = objectMapper.readTree(json);
            String itemText = node.path("item").path("text").asText("");
            if (!itemText.isEmpty() && builder.indexOf(itemText) == -1) { builder.append(itemText); return; }
            String deltaText = node.path("delta").path("text").asText("");
            if (!deltaText.isEmpty()) { builder.append(deltaText); return; }
            JsonNode content = node.path("content");
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) if (part.has("text")) sb.append(part.get("text").asText());
                if (sb.length() > builder.length()) { builder.setLength(0); builder.append(sb); }
            }
        } catch (Exception ignored) {}
    }

    private String getHeader(McpTransportContext ctx, String name) {
        if (ctx == null) return null;
        Object v = ctx.get(name);
        if (v == null) v = ctx.get(name.toLowerCase());
        return v != null ? v.toString() : null;
    }
}
