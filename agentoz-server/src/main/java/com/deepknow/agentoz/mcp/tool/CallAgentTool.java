package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.dto.A2AContext;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.A2ATaskRegistry;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Call Agent Tool - 实现 Agent 间相互调用 (A2A 稳健版)
 */
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

    @AgentTool(name = "call_agent", description = "调用另一个Agent执行任务，实现Agent间协作。可以指定目标Agent名称和具体任务。")
    public String callAgent(
            io.modelcontextprotocol.common.McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标Agent的名称（如 PaperSearcher）", required = true) String targetAgentName,
            @AgentParam(name = "task", value = "要执行的任务描述", required = true) String task,
            @AgentParam(name = "context", value = "附加的上下文信息（可选）", required = false) String context
    ) {
        try {
            // 1. 提取 A2A 上下文
            String sourceAgentId = getHeader(ctx, "X-Agent-ID");
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            String traceId = getHeader(ctx, "X-A2A-Trace-ID");
            String originAgentId = getHeader(ctx, "X-A2A-Origin-Agent-ID");
            String parentTaskIdFromHeader = getHeader(ctx, "X-A2A-Parent-Task-ID");
            int currentDepth = 0;
            String depthStr = getHeader(ctx, "X-A2A-Depth");
            if (depthStr != null) {
                try {
                    currentDepth = Integer.parseInt(depthStr);
                } catch (NumberFormatException ignored) {}
            }

            // 2. 判定目标 Agent
            AgentEntity targetAgent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
                            .eq(AgentEntity::getAgentName, targetAgentName)
            );
            if (targetAgent == null) return String.format("Error: 找不到 Agent '%s'。", targetAgentName);

            // 3. 递归深度检查
            if (currentDepth >= 5) {
                return "Error: 任务嵌套层次太深，已拦截。";
            }

            // 4. 构建上下文链路
            String sourceAgentName = "Assistant";
            if (sourceAgentId != null) {
                AgentEntity sourceAgent = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, sourceAgentId));
                if (sourceAgent != null) sourceAgentName = sourceAgent.getAgentName();
            }

            final String parentTaskId = parentTaskIdFromHeader != null ? parentTaskIdFromHeader : conversationId;
            A2AContext parentA2aContext = A2AContext.builder()
                    .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                    .parentTaskId(parentTaskIdFromHeader)
                    .depth(currentDepth)
                    .originAgentId(originAgentId != null ? originAgentId : sourceAgentId)
                    .build();
            A2AContext childA2aContext = parentA2aContext.next(parentTaskId);

            // 5. 准备执行
            final CompletableFuture<String> resultFuture = new CompletableFuture<>();
            final StringBuilder fullResponse = new StringBuilder();

            AgentExecutionManager.ExecutionContextExtended executionContext = 
                    new AgentExecutionManager.ExecutionContextExtended(
                            targetAgent.getAgentId(),
                            conversationId,
                            task + (context != null ? "\n\nContext:\n" + context : ""),
                            "assistant",
                            sourceAgentName,
                            true,
                            childA2aContext
                    );

            agentExecutionManager.executeTaskExtended(
                    executionContext,
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        
                        // ⭐ A2A 事件路由：直接推给父会话流
                        a2aTaskRegistry.sendEvent(parentTaskId, event);

                        // ⭐ 稳健的文本提取逻辑
                        extractText(event, fullResponse);
                    },
                    () -> {
                        // 任务完成，返回最终累计的文本
                        String result = fullResponse.toString().trim();
                        log.info("[CallAgent] ✓ 子任务执行完成, 返回长度: {}", result.length());
                        resultFuture.complete(result);
                    },
                    (Throwable t) -> {
                        log.error("[CallAgent] ✗ 子任务执行异常", t);
                        resultFuture.completeExceptionally(t);
                    }
            );

            // 6. 阻塞等待结果 (Codex 硬超时为 60s)
            String finalResult = resultFuture.get(30, TimeUnit.MINUTES);
            if (finalResult.isEmpty()) {
                return "Error: 子智能体执行完成，但未返回任何有效文本内容。";
            }
            return finalResult;

        } catch (Exception e) {
            log.error("CallAgent 执行异常", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 从 InternalCodexEvent 中精准提取文本内容
     * 兼容 Delta (增量) 和 Final (全量) 模式
     */
    private void extractText(InternalCodexEvent event, StringBuilder builder) {
        try {
            // 1. 尝试通过标准 Converter 提取增量
            TaskResponse respDto = TaskResponseConverter.toTaskResponse(event);
            if (respDto != null && respDto.getTextDelta() != null) {
                builder.append(respDto.getTextDelta());
                return;
            }
            
            // 2. 如果增量为空，尝试解析原始 JSON 提取全量内容 (针对 agent_message 事件)
            String rawJson = event.getRawEventJson();
            if (rawJson != null) {
                JsonNode node = objectMapper.readTree(rawJson);
                if ("agent_message".equals(event.getEventType())) {
                    JsonNode content = node.path("content");
                    if (content.isArray()) {
                        StringBuilder fullText = new StringBuilder();
                        for (JsonNode item : content) {
                            if (item.has("text")) fullText.append(item.get("text").asText());
                        }
                        if (fullText.length() > 0) {
                            // 如果全量结果大于当前已收集的增量，则进行同步/覆盖 (简化逻辑：如果 builder 空则填入)
                            if (builder.length() == 0) builder.append(fullText);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取文本响应失败: {}", e.getMessage());
        }
    }

    private String getHeader(McpTransportContext ctx, String headerName) {
        if (ctx == null) return null;
        Object val = ctx.get(headerName);
        if (val == null) val = ctx.get(headerName.toLowerCase());
        return val != null ? val.toString() : null;
    }
}