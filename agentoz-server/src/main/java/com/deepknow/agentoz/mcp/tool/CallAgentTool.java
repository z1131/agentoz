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
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

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
            @AgentParam(name = "task", value = "任务描述") String task,
            @AgentParam(name = "context", value = "上下文", required = false) String context
    ) {
        String subTaskId = UUID.randomUUID().toString();
        try {
            String sourceAgentId = getHeader(ctx, "X-Agent-ID");
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            String parentTaskIdFromHeader = getHeader(ctx, "X-A2A-Parent-Task-ID");
            int depth = 0;
            String d = getHeader(ctx, "X-A2A-Depth");
            if (d != null) try { depth = Integer.parseInt(d); } catch (Exception ignored) {}

            if (depth >= 5) return "Error: depth limit";

            AgentEntity target = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId).eq(AgentEntity::getAgentName, targetAgentName));
            if (target == null) return "Error: agent not found";

            String sourceName = "Assistant";
            if (sourceAgentId != null) {
                AgentEntity source = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, sourceAgentId));
                if (source != null) sourceName = source.getAgentName();
            }

            final String parentTaskId = parentTaskIdFromHeader != null ? parentTaskIdFromHeader : conversationId;
            A2AContext childCtx = A2AContext.builder()
                    .traceId(getHeader(ctx, "X-A2A-Trace-ID") != null ? getHeader(ctx, "X-A2A-Trace-ID") : UUID.randomUUID().toString())
                    .parentTaskId(parentTaskId)
                    .depth(depth + 1)
                    .originAgentId(getHeader(ctx, "X-A2A-Origin-Agent-ID") != null ? getHeader(ctx, "X-A2A-Origin-Agent-ID") : sourceAgentId)
                    .build();

            final StringBuilder res = new StringBuilder();
            agentExecutionManager.executeTaskExtended(new AgentExecutionManager.ExecutionContextExtended(target.getAgentId(), conversationId, task, "assistant", sourceName, true, childCtx), (InternalCodexEvent event) -> {
                if (event == null) return;
                event.setSenderName(targetAgentName);
                a2aTaskRegistry.sendEvent(parentTaskId, event);
                collectText(event, res);
            }, () -> {
                a2aTaskRegistry.completeTask(subTaskId, res.toString());
            }, (Throwable t) -> {
                a2aTaskRegistry.completeTask(subTaskId, "Error: " + t.getMessage());
            });

            return A2A_DELEGATED_MARKER + subTaskId + "]";
        } catch (Exception e) {
            log.error("CallAgent fail", e);
            return "Error: " + e.getMessage();
        }
    }

    private void collectText(InternalCodexEvent event, StringBuilder builder) {
        try {
            if (event.getRawEventJson() == null) return;
            JsonNode node = objectMapper.readTree(event.getRawEventJson());
            if ("agent_message_delta".equals(event.getEventType())) {
                if (node.path("delta").has("text")) builder.append(node.path("delta").path("text").asText());
            } else if ("agent_message".equals(event.getEventType())) {
                JsonNode c = node.path("content");
                if (c.isArray() && builder.length() == 0) for (JsonNode i : c) if (i.has("text")) builder.append(i.get("text").asText());
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
