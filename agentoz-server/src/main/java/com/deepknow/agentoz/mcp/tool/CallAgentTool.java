package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.dto.A2AContext;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.A2ATaskRegistry;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.a2a.spec.Task;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskState;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
    private A2ATaskRegistry a2aTaskRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    public static final String A2A_DELEGATED_MARKER = "[A2A_TASK_DELEGATED:";

    @AgentTool(name = "call_agent", description = "异步委派任务")
    public String callAgent(
            McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标名称") String targetAgentName,
            @AgentParam(name = "task", value = "任务指令") String task
    ) {
        String subId = UUID.randomUUID().toString();
        try {
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            AgentEntity target = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId).eq(AgentEntity::getAgentName, targetAgentName));
            if (target == null) return "Error: Target not found";

            A2AContext childCtx = A2AContext.builder()
                    .traceId(getHeader(ctx, "X-A2A-Trace-ID") != null ? getHeader(ctx, "X-A2A-Trace-ID") : UUID.randomUUID().toString())
                    .parentTaskId(conversationId)
                    .depth(1)
                    .originAgentId(getHeader(ctx, "X-Agent-ID"))
                    .build();

            final StringBuilder res = new StringBuilder();
            agentExecutionManager.executeTaskExtended(new AgentExecutionManager.ExecutionContextExtended(
                    target.getAgentId(), conversationId, task, "assistant", "System", true, childCtx), 
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        a2aTaskRegistry.sendEvent(conversationId, event);
                        collectText(event, res);
                    }, 
                    () -> {
                        // 官方规范：Task 使用全量构造，TaskStatus 使用 state() 对应的构造
                        Task completed = new Task(subId, conversationId, new TaskStatus(TaskState.COMPLETED, null, OffsetDateTime.now()), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
                        a2aTaskRegistry.updateTask(subId, completed);
                    }, 
                    (Throwable t) -> {
                        Task failed = new Task(subId, conversationId, new TaskStatus(TaskState.FAILED, null, OffsetDateTime.now()), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
                        a2aTaskRegistry.updateTask(subId, failed);
                    });

            Task initialTask = new Task(subId, conversationId, new TaskStatus(TaskState.SUBMITTED, null, OffsetDateTime.now()), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
            return objectMapper.writeValueAsString(initialTask);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private void collectText(InternalCodexEvent event, StringBuilder builder) {
        try {
            if (event.getRawEventJson() == null) return;
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(event.getRawEventJson());
            if ("agent_message_delta".equals(event.getEventType())) {
                if (node.path("delta").has("text")) builder.append(node.path("delta").path("text").asText());
            } else if ("agent_message".equals(event.getEventType())) {
                com.fasterxml.jackson.databind.JsonNode c = node.path("content");
                if (c.isArray() && builder.length() == 0) for (com.fasterxml.jackson.databind.JsonNode i : c) if (i.has("text")) builder.append(i.get("text").asText());
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