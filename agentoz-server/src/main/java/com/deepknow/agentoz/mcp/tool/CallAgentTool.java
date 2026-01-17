package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CallAgentTool {

    @Autowired
    private AgentExecutionManager agentExecutionManager;
    @Autowired
    private AgentRepository agentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @AgentTool(name = "call_agent", description = "委派任务给另一个智能体并等待其完成。")
    public String callAgent(
            McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标智能体名称") String targetAgentName,
            @AgentParam(name = "task", value = "任务指令") String task
    ) {
        try {
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            AgentEntity target = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId).eq(AgentEntity::getAgentName, targetAgentName));
            if (target == null) return "Error: Target agent not found";

            final CompletableFuture<String> resultFuture = new CompletableFuture<>();
            final StringBuilder resAccumulator = new StringBuilder();
            
            log.info("[CallAgent] 🔄 Starting synchronous delegation (waiting up to 55s): {} -> {}", "System", targetAgentName);

            // 启动子任务
            agentExecutionManager.executeTaskExtended(new AgentExecutionManager.ExecutionContextExtended(
                    target.getAgentId(), conversationId, task, "assistant", "System", true), 
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        // 实时透传事件给前端流
                        agentExecutionManager.broadcastSubTaskEvent(conversationId, event);
                        collectText(event, resAccumulator);
                    }, 
                    () -> {
                        // 子任务完成：唤醒当前线程
                        resultFuture.complete(resAccumulator.toString());
                    }, 
                    (Throwable t) -> {
                        resultFuture.completeExceptionally(t);
                    });

            // ⭐ 核心逻辑：挂起当前 Java 线程，等待子智能体结果 (Codex 60s 超时防护)
            String result = resultFuture.get(55, TimeUnit.SECONDS);
            
            if (result == null || result.isBlank()) {
                return "子智能体已完成，但未返回任何内容。";
            }
            
            log.info("[CallAgent] ✅ Result received, returning to caller LLM.");
            return result;

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[CallAgent] ⚠️ Delegation timed out at 55s.");
            return "任务正在处理中，由于耗时较长，请稍后再次确认进度。";
        } catch (Exception e) {
            log.error("CallAgent execution fail", e);
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
