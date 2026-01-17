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
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @AgentTool(name = "call_agent", description = "正统 A2A 异步任务委派")
    public String callAgent(
            McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标智能体") String targetAgentName,
            @AgentParam(name = "task", value = "任务指令") String task
    ) {
        String subId = UUID.randomUUID().toString();
        try {
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            AgentEntity target = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId).eq(AgentEntity::getAgentName, targetAgentName));
            if (target == null) return "Error: Target not found";

            final StringBuilder resAccumulator = new StringBuilder();
            
            // 启动子任务 (直接通过 Manager 执行)
            agentExecutionManager.executeTaskExtended(new AgentExecutionManager.ExecutionContextExtended(
                    target.getAgentId(), conversationId, task, "assistant", "System", true), 
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        // 流式透传 (模拟 A2A Event 路由)
                        agentExecutionManager.broadcastSubTaskEvent(conversationId, event);
                        collectText(event, resAccumulator);
                    }, 
                    () -> {
                        // 子任务完成：更新 TaskStore
                        Task completed = new Task(subId, conversationId, new TaskStatus(TaskState.COMPLETED, null, OffsetDateTime.now()), 
                                List.of(new Artifact(UUID.randomUUID().toString(), "Result", null, List.of(new TextPart(resAccumulator.toString())), Collections.emptyMap(), Collections.emptyList())), 
                                Collections.emptyList(), Collections.emptyMap());
                        taskStore.save(completed);
                    }, 
                    (Throwable t) -> {
                        Task failed = new Task(subId, conversationId, new TaskStatus(TaskState.FAILED, null, OffsetDateTime.now()), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
                        taskStore.save(failed);
                    });

            // 正统返回：初始 Task 状态
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
                if (c.isArray()) {
                    StringBuilder fullText = new StringBuilder();
                    for (com.fasterxml.jackson.databind.JsonNode i : c) if (i.has("text")) fullText.append(i.get("text").asText());
                    if (fullText.length() > builder.length()) { builder.setLength(0); builder.append(fullText); }
                }
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