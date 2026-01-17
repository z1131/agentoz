package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.dto.A2AContext;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.SessionStreamRegistry;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Call Agent Tool - 实现 Agent 间相互调用
 */
@Slf4j
@Component
public class CallAgentTool {

    @Autowired
    private AgentExecutionManager agentExecutionManager;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private SessionStreamRegistry sessionStreamRegistry;

    @AgentTool(name = "call_agent", description = "调用另一个Agent执行任务，实现Agent间协作。可以指定目标Agent名称和具体任务。")
    public String callAgent(
            io.modelcontextprotocol.common.McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标Agent的名称（如 PaperSearcher）", required = true) String targetAgentName,
            @AgentParam(name = "task", value = "要执行的任务描述", required = true) String task,
            @AgentParam(name = "context", value = "附加的上下文信息（可选）", required = false) String context
    ) {
        try {
            log.info("[CallAgent] 开始处理调用请求, targetAgentName={}, task={}",
                    targetAgentName, task);

            // 1. 从 MCP Transport Context 获取会话信息和 A2A 上下文
            String sourceAgentId = "unknown";
            String sourceAgentName = "Assistant";
            String conversationId = null;
            
            // A2A 上下文相关
            String traceId = null;
            int currentDepth = 0;
            String originAgentId = null;

            if (ctx != null) {
                // 从请求头获取会话和 A2A 信息（支持大小写不敏感）
                conversationId = getHeader(ctx, "X-Conversation-ID");
                sourceAgentId = getHeader(ctx, "X-Agent-ID");
                
                // ⭐ 提取 A2A 协议头
                traceId = getHeader(ctx, "X-A2A-Trace-ID");
                originAgentId = getHeader(ctx, "X-A2A-Origin-Agent-ID");
                String depthStr = getHeader(ctx, "X-A2A-Depth");
                if (depthStr != null) {
                    try {
                        currentDepth = Integer.parseInt(depthStr);
                    } catch (NumberFormatException ignored) {}
                }

                log.info("[CallAgent] 提取 A2A 上下文 - TraceId: {}, Depth: {}, OriginAgent: {}",
                        traceId, currentDepth, originAgentId);
            }

            // 2. 递归深度检查 (死循环防护)
            if (currentDepth >= 5) {
                log.error("[CallAgent] ✗ 触发递归深度限制: depth={}", currentDepth);
                return "Error: 任务嵌套层次太深（已达到5层），为了系统安全已拦截调用。请检查是否存在 Agent 间的循环调用。";
            }

            // 查找发送者名称
            if (sourceAgentId != null && !sourceAgentId.equals("unknown")) {
                try {
                    AgentEntity sourceAgent = agentRepository.selectOne(
                            new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, sourceAgentId)
                    );
                    if (sourceAgent != null) {
                        sourceAgentName = sourceAgent.getAgentName();
                    }
                } catch (Exception e) {
                    log.warn("[CallAgent] 查找发送者名称失败: {}", e.getMessage());
                }
            }

            // 验证会话ID
            if (conversationId == null) {
                log.error("[CallAgent] ✗ 无法获取会话ID，调用失败");
                return "Error: 无法获取当前会话ID，请联系管理员检查系统配置。";
            }

            // 3. 解析目标 Agent ID (Name -> ID)
            AgentEntity targetAgent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
                            .eq(AgentEntity::getAgentName, targetAgentName)
            );

            if (targetAgent == null) {
                return String.format("Error: 在当前会话中找不到名为 '%s' 的 Agent。请确认目标 Agent 名称是否正确。", targetAgentName);
            }

            String targetAgentId = targetAgent.getAgentId();

            // 4. 构建消息 (合并 context)
            String finalMessage = task;
            if (context != null && !context.isBlank()) {
                finalMessage = String.format("%s\n\n[Context]\n%s", task, context);
            }

            // 5. ⭐ 构建子任务 A2A 上下文
            A2AContext parentA2aContext = A2AContext.builder()
                    .traceId(traceId != null ? traceId : java.util.UUID.randomUUID().toString())
                    .depth(currentDepth)
                    .originAgentId(originAgentId != null ? originAgentId : sourceAgentId)
                    .build();
            
            // 派生下一级上下文
            A2AContext childA2aContext = parentA2aContext.next(sourceAgentId);

            log.info("[CallAgent] → 发起委派: Source[{}] -> Target[{}], TraceId={}, NewDepth={}",
                    sourceAgentName, targetAgentName, childA2aContext.getTraceId(), childA2aContext.getDepth());

            final String currentConversationId = conversationId;
            final String finalTargetAgentName = targetAgentName;

            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            final StringBuilder fullResponse = new StringBuilder();

            // 6. 执行子任务
            AgentExecutionManager.ExecutionContextExtended executionContext =
                    new AgentExecutionManager.ExecutionContextExtended(
                            targetAgentId,
                            conversationId,
                            finalMessage,
                            "assistant",
                            sourceAgentName,
                            true,
                            childA2aContext // ⭐ 显式传递 A2A 上下文
                    );

            agentExecutionManager.executeTaskExtended(
                    executionContext,
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        try {
                            event.setSenderName(finalTargetAgentName);
                            sessionStreamRegistry.broadcast(currentConversationId, event);
                        } catch (Exception e) {
                            log.warn("[CallAgent] 广播子任务事件失败: {}", e.getMessage());
                        }

                        TaskResponse respDto = TaskResponseConverter.toTaskResponse(event);
                        if (respDto != null && respDto.getTextDelta() != null) {
                            fullResponse.append(respDto.getTextDelta());
                        } else if (respDto != null && respDto.getFinalResponse() != null) {
                            fullResponse.setLength(0);
                            fullResponse.append(respDto.getFinalResponse());
                        }
                    },
                    () -> {
                        log.info("[CallAgent] ✓ 委派任务完成: {}", childA2aContext.getTraceId());
                        resultFuture.complete(fullResponse.toString());
                    },
                    (Throwable throwable) -> {
                        log.error("[CallAgent] ✗ 委派任务异常", throwable);
                        resultFuture.completeExceptionally(throwable);
                    }
            );

            // 7. 等待结果 (临时保留阻塞，等第三阶段实现完全异步)
            String result = resultFuture.get(30, TimeUnit.MINUTES);
            return result;

        } catch (Exception e) {
            log.error("CallAgent 工具执行异常", e);
            return "Error: 工具执行失败 - " + e.getMessage();
        }
    }

    private String getHeader(McpTransportContext ctx, String headerName) {
        Object val = ctx.get(headerName);
        if (val == null) {
            val = ctx.get(headerName.toLowerCase());
        }
        return val != null ? val.toString() : null;
    }

    private String extractTokenFromMap(java.util.Map<?, ?> map) {
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toString().equalsIgnoreCase("Authorization")) {
                String val = entry.getValue().toString();
                if (val.startsWith("Bearer ")) {
                    return val.substring(7);
                }
                return val;
            }
        }
        return null;
    }
}