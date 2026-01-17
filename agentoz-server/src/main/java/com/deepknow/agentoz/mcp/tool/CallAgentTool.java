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
import com.fasterxml.jackson.databind.JsonNode;
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

    public static final String A2A_DELEGATED_MARKER = "[A2A_TASK_DELEGATED:";

    @AgentTool(name = "call_agent", description = "委派任务给另一个智能体")
    public String callAgent(
            McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标名称") String targetAgentName,
            @AgentParam(name = "task", value = "指令") String task
    ) {
        try {
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            AgentEntity target = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId).eq(AgentEntity::getAgentName, targetAgentName));
            if (target == null) return "Error: Target not found";

            final CompletableFuture<String> resultFuture = new CompletableFuture<>();
            final StringBuilder resAccumulator = new StringBuilder();

            agentExecutionManager.executeTaskExtended(new AgentExecutionManager.ExecutionContextExtended(
                    target.getAgentId(), conversationId, task, "assistant", "System", true), 
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        agentExecutionManager.broadcastSubTaskEvent(conversationId, event);
                        // ⭐ 核心：全路径抓取文本
                        extractTextRobustly(event, resAccumulator);
                    }, 
                    () -> {
                        String finalResult = resAccumulator.toString().trim();
                        log.info("[CallAgent] Subtask completed. Content length: {}", finalResult.length());
                        resultFuture.complete(finalResult);
                    }, 
                    (Throwable t) -> {
                        resultFuture.completeExceptionally(t);
                    });

            return resultFuture.get(55, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("CallAgent error", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 鲁棒的文本提取：不管是 delta, agent_message 还是 item.completed，只要有文字就抓出来
     */
    private void extractTextRobustly(InternalCodexEvent event, StringBuilder accumulator) {
        try {
            String json = event.getRawEventJson();
            if (json == null) return;
            JsonNode node = objectMapper.readTree(json);
            
            // 1. 抓取 item.text (针对 item.completed 事件)
            String itemText = node.path("item").path("text").asText("");
            if (!itemText.isEmpty() && accumulator.indexOf(itemText) == -1) {
                accumulator.append(itemText);
                return;
            }

            // 2. 抓取 delta.text (针对增量事件)
            String deltaText = node.path("delta").path("text").asText("");
            if (!deltaText.isEmpty()) {
                accumulator.append(deltaText);
                return;
            }

            // 3. 抓取 content 数组 (针对 OpenAI 风格全量事件)
            JsonNode content = node.path("content");
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    if (part.has("text")) sb.append(part.get("text").asText());
                }
                if (sb.length() > accumulator.length()) {
                    accumulator.setLength(0);
                    accumulator.append(sb);
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